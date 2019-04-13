package se.quizhelp

import android.view.WindowManager
import android.view.Gravity
import android.graphics.PixelFormat
import android.view.LayoutInflater
import android.content.Intent
import android.os.IBinder
import android.app.Service
import android.content.Context
import android.graphics.BitmapFactory
import android.view.View
import android.widget.ImageView
import android.graphics.Point
import android.os.Handler
import android.view.MotionEvent
import android.widget.TextView
import com.github.kittinunf.fuel.android.extension.responseJson
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import java.io.File
import android.graphics.Bitmap
import android.util.Base64
import com.google.android.gms.tasks.Tasks
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import io.socket.client.IO
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.util.ArrayList
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class OverlayService : Service() {

    var previous_question = ""
    var previous_options = ""
    val FirebaseOCRText = ArrayList<ScreenshotText>()

    fun FirebaseOCR() {

        FirebaseOCRText.clear()

        val imageFile = File("/storage/emulated/0/quiz_bot_question.png")
        if (imageFile.exists()) {
            val bitmap = BitmapFactory.decodeFile("/storage/emulated/0/quiz_bot_question.png")
            if (bitmap != null) {
                val image = FirebaseVisionImage.fromBitmap(bitmap)
                val detector = FirebaseVision.getInstance().getOnDeviceTextRecognizer()
                val result = detector.processImage(image)

                Tasks.await(result.addOnSuccessListener { firebaseVisionText ->

                    for (i in firebaseVisionText.textBlocks) {
                        val boundingBox = i.boundingBox
                        val OCR_text = i.text
                        val screenshotText = ScreenshotText(
                            OCR_text,
                            boundingBox!!.left,
                            boundingBox.right,
                            boundingBox.top,
                            boundingBox.bottom
                        )
                        FirebaseOCRText.add(screenshotText)
                    }
                }, 10000, TimeUnit.MILLISECONDS)
            }

            Thread.sleep(100)

        }
    }

    private var mWindowManager: WindowManager? = null
    private var mChatHeadView: View? = null

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        val socket = IO.socket("http://85.229.17.70:7000")
        socket.connect()

        val window = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = window.defaultDisplay
        val size = Point()
        display.getSize(size)
        val screenWidth = size.x
        val screenHeight = size.y

        mChatHeadView = LayoutInflater.from(this).inflate(R.layout.floating, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        mWindowManager = getSystemService(WINDOW_SERVICE) as WindowManager?
        val screenSize = Point(0, 0)
        mWindowManager!!.defaultDisplay.getSize(screenSize)
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = screenSize.y

        mWindowManager!!.addView(mChatHeadView, params)

        val answer_1 = mChatHeadView!!.findViewById(R.id.answer_1) as TextView
        val answer_2 = mChatHeadView!!.findViewById(R.id.answer_2) as TextView
        val answer_3 = mChatHeadView!!.findViewById(R.id.answer_3) as TextView
        val answer_4 = mChatHeadView!!.findViewById(R.id.answer_4) as TextView
        answer_4.setVisibility(View.GONE)

        val closeButton = mChatHeadView?.findViewById(R.id.close_btn) as ImageView
        closeButton.setOnClickListener({
            stopSelf()
        })

        fun PhotoAnalysis() {

            Screenshot("quiz_bot_image.png").waitFor()

            val imageFile = File("/storage/emulated/0/quiz_bot_image.png")
            if (imageFile.exists()) {
                val bitmap = BitmapFactory.decodeFile("/storage/emulated/0/quiz_bot_image.png")
                val bitmapCropped = Bitmap.createBitmap(bitmap, 0, 200, 1080, 1300)
                val baos = ByteArrayOutputStream()
                bitmapCropped.compress(Bitmap.CompressFormat.JPEG, 100, baos) //bm is the bitmap object
                val b = baos.toByteArray()
                val encodedImage = Base64.encodeToString(b, Base64.DEFAULT)


                val apiKey = "INSERT API KEY"
                val url = "https://vision.googleapis.com/v1/images:annotate?key=" + apiKey

                val bodyJson = """
                 {
                  "requests":[
                    {
                      "image":{
                            "content": "$encodedImage"
                      },
                      "features":[
                        {
                          "type":"WEB_DETECTION"
                        }
                      ]
                    }
                  ]
                }
                """

                url.httpPost().header("Content-Type" to "application/json").body(bodyJson)
                    .responseJson { request, response, result ->

                        try {

                            val webEntities = result.get().obj().getJSONArray("responses").getJSONObject(0).getJSONObject("webDetection").getJSONArray("webEntities")
                            var imageText = ""
                            var photoText = ""

                            for(i in 0 until webEntities.length()) {

                                try {
                                    if (i == webEntities.length() - 1) {
                                        imageText =
                                            imageText + webEntities.getJSONObject(i)["description"].toString() + " - " + webEntities.getJSONObject(
                                                i
                                            )["score"]
                                    } else {
                                        imageText =
                                            imageText + webEntities.getJSONObject(i)["description"].toString() + " - " + webEntities.getJSONObject(
                                                i
                                            )["score"] + "\n"
                                    }
                                } catch(e: Exception) {
                                    continue
                                }
                            }

                            socket.emit("photo_response", webEntities)

                            answer_4.setVisibility(View.VISIBLE)
                            answer_4.setText(imageText)
                        } catch(e: java.lang.Exception) {
                            println("Error with image search: " + e)
                        }
                    }
            }
        }

        answer_4.setOnClickListener {
            it.setVisibility(View.GONE)
        }

        val photoButton = mChatHeadView?.findViewById(R.id.photo_question) as ImageView
        photoButton.setOnClickListener({
            PhotoAnalysis()
        })

        val mHandler = Handler()

        val QuizBotMainFunction: Runnable = object : Runnable {

            override fun run() {

                mHandler.postDelayed(this, 1000)
                val activeProcess = GetRunningApp()

                if(activeProcess.contains("se.sventertainment.primetime", ignoreCase = true) || activeProcess.contains("com.google.android.apps.photos", ignoreCase = true)) {

                    mHandler.removeCallbacks(this)
                    var text = "Scanning for questions..."

                    Thread(Runnable {

                        while (text == "Scanning for questions...") {
                            Screenshot("quiz_bot_question.png").waitFor()
                            FirebaseOCR()
                            FirebaseOCRText.forEachIndexed { i, t ->

                                if(screenWidth == 1080 && screenHeight == 1800) {

                                    if (t.bottom < 560 && t.top > 250 && t.text.contains("?", ignoreCase = true)) {
                                        question = t.text
                                        text = "Question found!"
                                    } else if (t.bottom < 800 && t.top > 640) {
                                        option_1 = t.text
                                    } else if (t.bottom < 960 && t.top > 815) {
                                        option_2 = t.text
                                    } else if (t.bottom < 1130 && t.top > 985) {
                                        option_3 = t.text
                                    }

                                } else if(screenWidth == 720 && screenHeight == 1344) {

                                    if (t.bottom < 450 && t.top > 240 && t.text.contains("?", ignoreCase = true)) {
                                        question = t.text
                                        text = "Question found!"
                                    } else if (t.bottom < 630 && t.top > 520) {
                                        option_1 = t.text
                                    } else if (t.bottom < 770 && t.top > 650) {
                                        option_2 = t.text
                                    } else if (t.bottom < 900 && t.top > 790) {
                                        option_3 = t.text
                                    }

                                }

                            }
                        }

                        val options = ArrayList<Option>()
                        options.add(Option(option_1))
                        options.add(Option(option_2))
                        options.add(Option(option_3))
                        question = question.replace("\n", " ")

                        if(question != previous_question && options.toString() != previous_options) {

                            val apiKey = "INSERT API KEY"

                            val query = question.replace(" ", "+").replace("\"", "") + " -facebook.com"
                            val url = "https://www.googleapis.com/customsearch/v1?key=" + apiKey + "&cx=012174953912695344622:giuq4q7ueom&q=" + query + "&alt=json"
                            var searchResultString = ""
                            var ResultFromSearchEngineUrls = ""
                            var searchResult = JSONArray()

                            url.httpGet().responseJson { request, response, result ->

                                val responseJSON = result.get().obj()
                                try {
                                    searchResult = responseJSON.getJSONArray("items")
                                } catch(e: java.lang.Exception) {
                                    println("Error with API request: " + e)
                                }

                                val searchEngineUrls = ArrayList<String>()

                                for (i in 0 until (searchResult.length())) {

                                    val item = searchResult.getJSONObject(i)
                                    searchResultString = searchResultString + " " + item["title"] + " " + item["snippet"]

                                    var fileFormat = ""
                                    try {
                                        fileFormat = item["fileFormat"].toString()
                                    } catch (e: java.lang.Exception) {
                                        println("Error with file format: " + e)
                                    }

                                    if(!item["link"].toString().contains("pdf", ignoreCase = true) && !item["link"].toString().contains("doc", ignoreCase = true) && !fileFormat.contains("PDF", ignoreCase = true)) {
                                        searchEngineUrls.add(item["link"].toString())
                                    }
                                }

                                options.forEachIndexed { index, option ->
                                    val optionSplit = option.option.split(" ")
                                    for(i in optionSplit) {
                                        var word = i.replace(".", "(\\.|\\,)")
                                        if(word.length < 5) {
                                           word =  "(?<!\\d|\\w)" + word + "(?!\\d|\\w)"
                                        } else {
                                            word = "(?<!\\d|\\w)" + word
                                        }
                                        val matcher =
                                            Pattern.compile("(?i)" + word).matcher(searchResultString)
                                        while (matcher.find()) {
                                            options[index].hits += 15
                                        }
                                    }
                                }

                                Thread {

                                    try {

                                        for (i in 0..1) {
                                            Jsoup.connect(searchEngineUrls[i]).get().run {
                                                ResultFromSearchEngineUrls =
                                                    ResultFromSearchEngineUrls + this.body().text() + " "
                                            }
                                        }

                                        options.forEachIndexed { index, option ->
                                            val optionSplit = option.option.split(" ")
                                            for(i in optionSplit) {
                                                var word = i.replace(".", "(\\.|\\,)")
                                                if(word.length < 4) {
                                                    word =  "(?<!\\d|\\w)" + word + "(?!\\d|\\w)"
                                                } else {
                                                    word = "(?<!\\d|\\w)" + word
                                                }
                                                val matcher = Pattern.compile("(?i)" + word)
                                                    .matcher(ResultFromSearchEngineUrls)
                                                while (matcher.find()) {
                                                    options[index].hits += 1
                                                }
                                            }
                                        }

                                        options.forEachIndexed { index, option ->
                                            val optionSplit = option.option.split(" ")
                                            option.hits = option.hits / optionSplit.size
                                        }

                                        if(question.contains("inte", ignoreCase = true)) {
                                            val sortedOptions = options
                                            sortedOptions.sortByDescending { it.hits }
                                            val highestHit = sortedOptions[0].hits
                                            options.forEachIndexed{ index, option ->
                                                option.hits = highestHit - option.hits
                                            }
                                        }

                                        options.forEachIndexed { index, option ->
                                            options[index].percentage = options[index].hits / options.sumBy{it.hits}.toDouble() * 100
                                            options[index].percentage = Math.round(options[index].percentage * 100) / 100.0

                                            if(options[index].percentage.isNaN()) {
                                                options[index].percentage = 0.00
                                            }
                                        }

                                    } catch(e: Exception) {
                                        println("Error with Search Engine URLs: " + e)
                                    }

                                    mHandler.post({
                                        answer_1.setText(options[0].option + " - " + options[0].percentage + "%")
                                        answer_2.setText(options[1].option + " - " + options[1].percentage + "%")
                                        answer_3.setText(options[2].option + " - " + options[2].percentage + "%")
                                    })

                                    val JSONObject_1 = JSONObject()
                                    val JSONObject_2 = JSONObject()
                                    val JSONObject_3 = JSONObject()

                                    JSONObject_1.put("answer_1", options[0].option)
                                    JSONObject_1.put("probability", options[0].percentage)
                                    JSONObject_2.put("answer_2", options[1].option)
                                    JSONObject_2.put("probability", options[1].percentage)
                                    JSONObject_3.put("answer_3", options[2].option)
                                    JSONObject_3.put("probability", options[2].percentage)

                                    val JsonArrayAnswers = JSONArray()
                                    JsonArrayAnswers.put(JSONObject_1)
                                    JsonArrayAnswers.put(JSONObject_2)
                                    JsonArrayAnswers.put(JSONObject_3)

                                    socket.emit("answers", JsonArrayAnswers)

                                    previous_question = question
                                    previous_options = options.toString()

                                    this.run()

                                }.start()

                            }

                        } else {

                            println("Found previous question or options. Searching...")
                            this.run()

                        }

                    }).start()
                }
            }
        }

        fun startRepeatingTask() {
            QuizBotMainFunction.run()
        }

        startRepeatingTask()

        val answerImage = mChatHeadView!!.findViewById(R.id.chat_head_root) as View

        answerImage.setOnTouchListener(object : View.OnTouchListener {
            private var lastAction: Int = 0
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0.toFloat()
            private var initialTouchY: Float = 0.toFloat()

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {

                        initialX = params.x
                        initialY = params.y

                        initialTouchX = event.rawX
                        initialTouchY = event.rawY

                        lastAction = event.action
                        return true
                    }
                    MotionEvent.ACTION_UP -> {

                        lastAction = event.action
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()

                        mWindowManager!!.updateViewLayout(mChatHeadView, params)
                        lastAction = event.action
                        return true
                    }
                }
                return false
            }
        })

    }


    override fun onDestroy() {
        super.onDestroy()
        if (mChatHeadView != null) mWindowManager!!.removeView(mChatHeadView)
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}