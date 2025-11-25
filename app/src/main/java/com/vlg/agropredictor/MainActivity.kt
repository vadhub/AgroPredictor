package com.vlg.agropredictor

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions

class MainActivity : AppCompatActivity() {

    private val diseaseClassification by lazy { DiseaseClassificationMultimodal(this) }

    private var currentBitmap: Bitmap? = null

    // UI элементы
    private lateinit var temperatureInput: EditText
    private lateinit var humidityInput: EditText
    private lateinit var ageInput: EditText
    private lateinit var resultText: TextView
    private lateinit var detailedResultText: TextView
    private lateinit var detailedResultLayout: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var imageView: ImageView
    private lateinit var imagePlaceholder: ImageView

    //    private lateinit var overlayLayout: LinearLayout
    private lateinit var btnSelectImage: Button
    private lateinit var btnAnalyze: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        initializeViews()
        setupClickListeners()
    }

    private fun initializeViews() {
        // Поля метаданных
        temperatureInput = findViewById(R.id.temperatureInput)
        humidityInput = findViewById(R.id.humidityInput)
        ageInput = findViewById(R.id.ageInput)

        // Элементы результатов
        resultText = findViewById(R.id.resultText)
        detailedResultText = findViewById(R.id.detailedResultText)
        detailedResultLayout = findViewById(R.id.detailedResultLayout)

        // Прогресс и изображения
        progressBar = findViewById(R.id.progressBar)
        imageView = findViewById(R.id.imageView)
        imagePlaceholder = findViewById(R.id.imagePlaceholder)
//        overlayLayout = findViewById(R.id.overlayLayout)

        // Кнопки
        btnSelectImage = findViewById(R.id.btnSelectImage)
        btnAnalyze = findViewById(R.id.btnAnalyze)

        // Установка начальных значений
        setDefaultMetadataValues()
    }

    private fun setupClickListeners() {
        // Кнопка выбора изображения
        btnSelectImage.setOnClickListener {
            startCrop()
        }

        // Контейнер изображения (также кликабельный)
        val imageContainer = findViewById<FrameLayout>(R.id.imageContainer)
        imageContainer.setOnClickListener {
            startCrop()
        }

        // Кнопка анализа
        btnAnalyze.setOnClickListener {
            analyzeImage()
        }
    }

    private fun setDefaultMetadataValues() {
        // Установка типичных значений по умолчанию
        temperatureInput.setText("26.0")
        humidityInput.setText("88.0")
        ageInput.setText("35")
    }

    private fun analyzeImage() {
        // Проверка наличия изображения
        if (currentBitmap == null) {
            showToast("Пожалуйста, выберите изображение огурца")
            return
        }

        // Получение и валидация метаданных
        val metadata = getValidatedMetadata() ?: return

        // Показать прогресс
        progressBar.visibility = View.VISIBLE
        btnAnalyze.isEnabled = false

        // Запуск классификации
        diseaseClassification.classifyImage(
            currentBitmap!!,
            metadata.temperature,
            metadata.humidity,
            metadata.age
        ) { result ->
            progressBar.visibility = View.GONE
            btnAnalyze.isEnabled = true
            displayResults(result)
        }
    }

    private fun getValidatedMetadata(): Metadata? {
        return try {
            val temperature = temperatureInput.text.toString().toFloat()
            val humidity = humidityInput.text.toString().toFloat()
            val age = ageInput.text.toString().toFloat()

            // Валидация диапазонов
            when {
                temperature < 0 || temperature > 50 -> {
                    showToast("Температура должна быть между 0 и 50°C")
                    null
                }

                humidity < 0 || humidity > 100 -> {
                    showToast("Влажность должна быть между 0 и 100%")
                    null
                }

                age < 0 || age > 100 -> {
                    showToast("Возраст плода должен быть между 0 и 100 дней")
                    null
                }

                else -> Metadata(temperature, humidity, age)
            }
        } catch (e: NumberFormatException) {
            showToast("Пожалуйста, введите корректные числовые значения")
            null
        }
    }

    private fun displayResults(result: DiseaseClassificationMultimodal.ClassificationResult) {
        // Основной результат
        resultText.text = "${result.className} (${(result.confidence * 100).toInt()}%)"

        // Детальные результаты
        val detailedText = buildString {
            append("Детальный анализ:\n\n")
            result.allConfidences.forEachIndexed { index, confidence ->
                val className = diseaseClassification.getClassNames()[index]
                val percentage = (confidence * 100).toInt()
                append("$className: $percentage%\n")
            }
            append("\n").append(showDiseaseRecommendations(result.className))
        }

        detailedResultText.text = detailedText
        detailedResultLayout.visibility = View.VISIBLE

    }

//    private fun getEmojiForClass(classIndex: Int): String {
//        return when (classIndex) {
//            0 -> "🍂" // Anthracnose
//            1 -> "🦠" // Bacterial Wilt
//            2 -> "🍎" // Belly Rot
//            3 -> "💧" // Downy Mildew
//            4 -> "🥒" // Fresh Cucumber
//            5 -> "🌿" // Fresh Leaf
//            6 -> "🦠" // Gummy Stem Blight
//            7 -> "🍂" // Pythium Fruit Rot
//            else -> "❓"
//        }
//    }

    private fun showDiseaseRecommendations(className: String): String {
        val recommendations = when (className) {
            "Anthracnose" -> "Рекомендация: Обработать фунгицидами, уменьшить влажность"
            "Bacterial Wilt" -> "Рекомендация: Удалить пораженные растения, улучшить дренаж"
            "Belly Rot" -> "Рекомендация: Избегать контакта плодов с почвой, обработать противогрибковыми средствами"
            "Downy Mildew" -> "Рекомендация: Уменьшить влажность, улучшить вентиляцию"
            "Gummy Stem Blight" -> "Рекомендация: Обработать фунгицидами, удалить пораженные части"
            "Pythium Fruit Rot" -> "Рекомендация: Улучшить дренаж, обработать почву"
            else -> "Растение здоровое! Продолжайте ухаживать как обычно."
        }

        return recommendations
    }

    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val imageInput = result.getBitmap(this)
            currentBitmap = imageInput

            // Обновление UI
            imageView.setImageBitmap(imageInput)
            imageView.visibility = View.VISIBLE
            imagePlaceholder.visibility = View.GONE
//            overlayLayout.visibility = View.VISIBLE

            // Сброс предыдущих результатов
            resultText.text = "Изображение загружено. Нажмите 'Анализировать заболевание'"
            detailedResultLayout.visibility = View.GONE

            showToast("Изображение успешно загружено")
        } else {
            showToast("Не удалось загрузить изображение")
        }
    }

    private fun startCrop() {
        cropImage.launch(
            CropImageContractOptions(
                uri = null,
                cropImageOptions = CropImageOptions(
                    imageSourceIncludeGallery = true,
                    imageSourceIncludeCamera = true,
                    aspectRatioX = 1,
                    aspectRatioY = 1,
                    maxZoom = 4,
                    outputCompressFormat = Bitmap.CompressFormat.JPEG,
                    outputCompressQuality = 90
                ),
            ),
        )
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    // Data classes
    data class Metadata(val temperature: Float, val humidity: Float, val age: Float)
}