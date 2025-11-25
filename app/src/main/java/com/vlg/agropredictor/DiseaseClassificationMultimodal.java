package com.vlg.agropredictor;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.widget.Toast;

import com.vlg.agropredictor.ml.MultimodalCucumberModel;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class DiseaseClassificationMultimodal {

    private Context context;
    private final int imageSize = 224;

    public DiseaseClassificationMultimodal(Context context) {
        this.context = context;
    }

    public void classifyImage(Bitmap image, float temperature, float humidity, float age, Predict<ClassificationResult> predict) {
        try {
            // Нормализация метаданных
            float[] normalizedMetadata = MetadataNormalizer.normalizeMetadata(temperature, humidity, age);

            Log.d("!DiseaseClassification", String.format(
                    "Метаданные: %.1f°C, %.1f%%, %.0f дней -> Нормализованные: [%.3f, %.3f, %.3f]",
                    temperature, humidity, age,
                    normalizedMetadata[0], normalizedMetadata[1], normalizedMetadata[2]
            ));

            // Подготовка изображения
            image = convertBitmapToConfig(image, Bitmap.Config.ARGB_8888);
            image = Bitmap.createScaledBitmap(image, imageSize, imageSize, false);

            MultimodalCucumberModel model = MultimodalCucumberModel.newInstance(context);

            // Подготовка метаданных для модели
            TensorBuffer metadataInput = TensorBuffer.createFixedSize(new int[]{1, 3}, DataType.FLOAT32);
            ByteBuffer metadataBuffer = ByteBuffer.allocateDirect(3 * 4);
            metadataBuffer.order(ByteOrder.nativeOrder());
            for (float value : normalizedMetadata) {
                metadataBuffer.putFloat(value);
            }
            metadataBuffer.rewind();
            metadataInput.loadBuffer(metadataBuffer);

            // Подготовка изображения для модели
            TensorBuffer imageInput = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);
            ByteBuffer imageBuffer = convertBitmapToByteBuffer(image);
            imageInput.loadBuffer(imageBuffer);

            // Запуск inference
            Log.d("DiseaseClassification", "Запуск классификации...");
            MultimodalCucumberModel.Outputs outputs = model.process(metadataInput, imageInput);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();
            float[] confidence = outputFeature0.getFloatArray();

            // Обработка результатов
            int predictedClass = argMax(confidence);
            String className = getClassName(predictedClass);
            float maxConfidence = confidence[predictedClass];

            Log.d("DiseaseClassification", "Результаты: " + Arrays.toString(confidence));
            Log.d("DiseaseClassification", String.format(
                    "Предсказанный класс: %s (%.2f%%)", className, maxConfidence * 100
            ));

            // Создание результата
            ClassificationResult result = new ClassificationResult(className, maxConfidence, confidence);
            predict.predict(result);

            model.close();

        } catch (IOException e) {
            Log.e("DiseaseClassification", "Ошибка сети", e);
            Toast.makeText(context, "Ошибка сети: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("DiseaseClassification", "Ошибка классификации", e);
            Toast.makeText(context, "Ошибка классификации", Toast.LENGTH_SHORT).show();
        }
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4);
        byteBuffer.order(ByteOrder.nativeOrder());

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true);
        int[] intValues = new int[224 * 224];
        resizedBitmap.getPixels(intValues, 0, 224, 0, 0, 224, 224);

        int pixel = 0;
        for (int i = 0; i < 224; i++) {
            for (int j = 0; j < 224; j++) {
                int pixelValue = intValues[pixel++];

                // Извлечение RGB и нормализация для MobileNetV2
                float r = ((pixelValue >> 16) & 0xFF) / 255.0f;
                float g = ((pixelValue >> 8) & 0xFF) / 255.0f;
                float b = (pixelValue & 0xFF) / 255.0f;

                // MobileNetV2 предобработка: масштабирование от -1 до 1
                r = (r - 0.5f) * 2.0f;
                g = (g - 0.5f) * 2.0f;
                b = (b - 0.5f) * 2.0f;

                byteBuffer.putFloat(r);
                byteBuffer.putFloat(g);
                byteBuffer.putFloat(b);
            }
        }

        byteBuffer.rewind();
        return byteBuffer;
    }

    private int argMax(float[] array) {
        int maxIndex = 0;
        float maxValue = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i] > maxValue) {
                maxValue = array[i];
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    private String[] classNames = {
            "Anthracnose",
            "Bacterial Wilt",
            "Belly Rot",
            "Downy Mildew",
            "Fresh Cucumber",
            "Fresh Leaf",
            "Gummy Stem Blight",
            "Pythium Fruit Rot"
    };

    public String getClassName(int classIndex) {
        if (classIndex >= 0 && classIndex < classNames.length) {
            return classNames[classIndex];
        }
        return "Unknown";
    }

    public String[] getClassNames() {
        return classNames.clone();
    }

    private Bitmap convertBitmapToConfig(Bitmap bitmap, Bitmap.Config config) {
        if (bitmap.getConfig() == config) {
            return bitmap;
        }

        Bitmap convertedBitmap = bitmap.copy(config, false);
        if (convertedBitmap == null) {
            throw new RuntimeException("Could not convert Bitmap to config: " + config);
        }
        return convertedBitmap;
    }

    // Класс для хранения результатов классификации
    public static class ClassificationResult {
        private final String className;
        private final float confidence;
        private final float[] allConfidences;

        public ClassificationResult(String className, float confidence, float[] allConfidences) {
            this.className = className;
            this.confidence = confidence;
            this.allConfidences = allConfidences;
        }

        public String getClassName() {
            return className;
        }

        public float getConfidence() {
            return confidence;
        }

        public float[] getAllConfidences() {
            return allConfidences;
        }
    }
}