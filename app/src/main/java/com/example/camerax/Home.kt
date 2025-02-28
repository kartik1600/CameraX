package com.example.camerax

import android.annotation.SuppressLint
import android.content.ContentValues
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.camerax.databinding.FragmentHomeBinding
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutorService


class Home : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!


    private var imageCapture: ImageCapture? = null
    private var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var qualitySelector: QualitySelector = QualitySelector.from(Quality.FHD)
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(layoutInflater, container, false)
        return binding.root

    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        startCamera()
        binding.btnCaptureImage.setOnClickListener {
            takePhoto()
        }
        binding.btnRecordVideo.setOnClickListener {
            captureVideo()
        }
        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_SD -> {
                    qualitySelector = QualitySelector.from(Quality.SD)
                    startCamera()
                    true
                }

                R.id.action_HD -> {
                    qualitySelector = QualitySelector.from(Quality.HD)
                    startCamera()
                    true
                }

                R.id.action_FHD -> {
                    qualitySelector = QualitySelector.from(Quality.FHD)
                    startCamera()
                    true
                }

                R.id.action_UHD -> {
                    qualitySelector = QualitySelector.from(Quality.UHD)
                    startCamera()
                    it.collapseActionView()
                    true
                }

                R.id.action_change_camera -> {
                    changeCamera()
                    true
                }

                else -> {
                    Toast.makeText(requireContext(), "error", Toast.LENGTH_SHORT).show()
                    true
                }
            }
        }

    }


    private fun changeCamera() {

        when (cameraSelector) {
            CameraSelector.DEFAULT_BACK_CAMERA -> cameraSelector =
                CameraSelector.DEFAULT_FRONT_CAMERA

            CameraSelector.DEFAULT_FRONT_CAMERA -> cameraSelector =
                CameraSelector.DEFAULT_BACK_CAMERA

        }
        startCamera()

    }

    private fun startCamera() {
        val viewSetter = {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = binding.cameraPreview.surfaceProvider
                }
            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()
            videoCapture = VideoCapture.withOutput(recorder)
            imageCapture = ImageCapture.Builder().build()
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageCapture, videoCapture
            )
        }

        cameraProviderFuture.addListener(
            { viewSetter() },
            ContextCompat.getMainExecutor(requireContext())
        )
    }

    private fun takePhoto() {
        Log.e("TAG", "takePhoto: ")
        val imageCapture = imageCapture ?: return

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                requireContext().contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues(
                    displayName = "image",
                    format = "image/jpeg",
                    "Pictures/My-Photos"
                )
            )
            .build()


        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("TAG", "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    Log.d("TAG", msg)

                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        val isRecording = recording
        if (isRecording != null) {
            isRecording.stop()
            recording = null
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            requireContext().contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(
            contentValues(
                displayName = "videos",
                format = "video/mp4",
                "Movies/My-Videos"
            )
        ).build()

        recording = videoCapture.output.prepareRecording(
            requireContext(),
            outputOptions
        ).withAudioEnabled()
            .start(
                ContextCompat
                    .getMainExecutor(requireContext())
            ) {
                when (it) {
                    is VideoRecordEvent.Start -> {
                        binding.btnRecordVideo.text = "Stop"
                    }

                    is VideoRecordEvent.Finalize -> {
                        if (!it.hasError()) {
                            val msg = "URI-${it.outputResults.outputUri}"
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT)
                                .show()
                        } else {
                            recording?.close()
                            recording = null
                        }
                        binding.btnRecordVideo.text = "Record"
                    }
                }
            }
    }

    private fun contentValues(
        displayName: String,
        format: String,
        path: String
    ): ContentValues =
        ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, format)
            put(MediaStore.Images.Media.RELATIVE_PATH, path)
        }
}