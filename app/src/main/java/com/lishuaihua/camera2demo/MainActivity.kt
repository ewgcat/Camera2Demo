package com.lishuaihua.camera2demo

import android.Manifest
import android.content.Context.CAMERA_SERVICE
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.SparseIntArray
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import pub.devrel.easypermissions.EasyPermissions.PermissionCallbacks
import java.lang.Exception
import java.util.*

class MainActivity : AppCompatActivity(), PermissionCallbacks {
    lateinit var mSurfaceView: AutoFitSurfaceView
    lateinit var ivWaterCameraFlip: ImageView
    lateinit var ivShowCamera2Pic: ImageView
    lateinit var tvWaterCameraBack: TextView
    lateinit var tvLight: TextView

    private var mSurfaceHolder: SurfaceHolder? = null
    private var childHandler: Handler? = null

    /**
     * //摄像头Id 0 为后  1 为前
     */
    private var mCameraId = CameraCharacteristics.LENS_FACING_FRONT
    private var mImageReader: ImageReader? = null
    private var mCameraCaptureSession: CameraCaptureSession? = null
    private var mCameraDevice: CameraDevice? = null
    private var mainHandler: Handler? = null
    private val perms = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    private var torchLight = false
    private val ORIENTATION = SparseIntArray()
    private var mCameraManager: CameraManager? = null
    private var characteristics: CameraCharacteristics? = null
    // 创建拍照需要的CaptureRequest.Builder
    var captureRequestBuilder: CaptureRequest.Builder? = null
    var previewRequestBuilder: CaptureRequest.Builder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initView()
        initListener()
        startPermissionsTask()
    }

    /**
     * 初始化界面
     */
    private fun initView() {
        mSurfaceView = findViewById(R.id.autoFitSurfaceView)
        ivWaterCameraFlip = findViewById<ImageView>(R.id.iv_water_camera_flip)
        ivShowCamera2Pic = findViewById<ImageView>(R.id.iv_show_camera2_pic)
        tvWaterCameraBack = findViewById(R.id.tv_water_camera_back)
        tvLight = findViewById(R.id.tv_light)

    }

    /**
     * 初始化监听事件
     */
    private fun initListener() {
        tvLight.setOnClickListener {
            torchLight = !torchLight
            tvLight.text = if (torchLight) "关闭闪光灯" else "打开闪光灯"
            openFlash(torchLight)
        }
        findViewById<ImageView>(R.id.iv_photo_graph).setOnClickListener {
            takePicture()
        }
        tvWaterCameraBack.setOnClickListener {
            mSurfaceView.visibility = View.VISIBLE
            ivShowCamera2Pic.visibility = View.GONE
            ivWaterCameraFlip.visibility = View.VISIBLE
            openCamera(true)
        }
        ivWaterCameraFlip.setOnClickListener {
            if (CameraCharacteristics.LENS_FACING_FRONT == mCameraId) {
                mCameraId = CameraCharacteristics.LENS_FACING_BACK
                front()
            } else {
                mCameraId = CameraCharacteristics.LENS_FACING_FRONT
                rear()
            }
            openCamera(true)
        }
        mCameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            characteristics = mCameraManager!!.getCameraCharacteristics(mCameraId.toString() + "")
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }

        mSurfaceHolder = mSurfaceView.holder
        mSurfaceHolder!!.setKeepScreenOn(true)
        // mSurfaceView添加回调
        mSurfaceHolder!!.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                val previewSize = getPreviewOutputSize(
                    mSurfaceView.display,
                    characteristics!!,
                    SurfaceHolder::class.java,
                    null
                )
                mSurfaceView.setAspectRatio(
                    previewSize.width,
                    previewSize.height
                )
                //SurfaceView创建
                // 初始化Camera
                initCamera2()
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                //SurfaceView销毁
                // 释放Camera资源
                if (null != mCameraDevice) {
                    mCameraDevice!!.close()
                    mCameraDevice = null
                }
            }
        })
        rear()
    }

    /**
     * 初始化Camera2
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun initCamera2() {
        val handlerThread = HandlerThread("Camera2")
        handlerThread.start()
        childHandler = Handler(handlerThread.looper)
        mainHandler = Handler(mainLooper)
        openCamera(false)
    }

    /**
     * 打开相机预览
     */
    private fun openCamera(isFlip: Boolean) {
        //获取摄像头管理
        //摄像头管理器
        if (isFlip) {
            // 关闭捕获会话
            if (null != mCameraCaptureSession) {
                mCameraCaptureSession!!.close()
                mCameraCaptureSession = null
            }
            // 关闭当前相机
            if (null != mCameraDevice) {
                mCameraDevice!!.close()
                mCameraDevice = null
            }
            // 关闭拍照处理器
            if (null != mImageReader) {
                mImageReader!!.close()
                mImageReader = null
            }
        }
        if (mCameraManager == null) {
            mCameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        }
        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            if (mImageReader == null) {
                if (characteristics == null) {
                    characteristics =
                        mCameraManager!!.getCameraCharacteristics(Integer.toString(mCameraId))
                }
                val previewSize = getPreviewOutputSize(
                    mSurfaceView.display,
                    characteristics!!,
                    SurfaceHolder::class.java,
                    null
                )
                mImageReader = ImageReader.newInstance(
                    previewSize.width,
                    previewSize.height,
                    ImageFormat.JPEG,
                    1
                )
                //可以在这里处理拍照得到的临时照片 例如，写入本地
                mImageReader!!.setOnImageAvailableListener(OnImageAvailableListener { reader: ImageReader ->
                    if (null != mCameraDevice) {
                        mCameraDevice!!.close()
                        mCameraDevice = null
                    }
                    mSurfaceView.visibility = View.GONE
                    ivShowCamera2Pic.setVisibility(View.VISIBLE)
                    ivWaterCameraFlip.setVisibility(View.GONE)
                    // 拿到拍照照片数据
                    val image = reader.acquireNextImage()
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer[bytes]
                    //由缓冲区存入字节数组
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        var bitmapConfig: Bitmap.Config = bitmap.getConfig()
                        if (bitmapConfig == null) {
                            bitmapConfig = Bitmap.Config.ARGB_8888
                        }

                        val rotation = windowManager.defaultDisplay.rotation
                        val matrix = Matrix()
                        matrix.setRotate(
                          ORIENTATION.get(
                                rotation
                            ).toFloat()
                        )
                        if (mCameraId == 1) {
                            // 前置摄像头镜像水平翻转
                            matrix.postScale(-1f, 1f)
                        }

                        val testBitMap = Bitmap.createBitmap(
                            bitmap,
                            0,
                            0,
                            bitmap.width,
                            bitmap.height,
                            matrix,
                            false
                        )

                        ivShowCamera2Pic.setImageBitmap(testBitMap)
                    }
                }, mainHandler)
            }

            //打开摄像头
            mCameraManager!!.openCamera(mCameraId.toString() + "", stateCallback, mainHandler)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 摄像头创建监听
     */
    private val stateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            //打开摄像头
            mCameraDevice = camera
            //开启预览
            takePreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            //关闭摄像头
            if (null != mCameraDevice) {
                mCameraDevice!!.close()
                mCameraDevice = null
            }
        }

        override fun onError(camera: CameraDevice, error: Int) {
            //发生错误
            Toast.makeText(this@MainActivity, "摄像头开启失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFlash(isStartFlash: Boolean) {
        try {
            if (isStartFlash) {
                // 打开闪光灯
                previewRequestBuilder!!.set(
                    CaptureRequest.FLASH_MODE,
                    CaptureRequest.FLASH_MODE_TORCH
                )
            } else {
                previewRequestBuilder!!.set(
                    CaptureRequest.FLASH_MODE,
                    CaptureRequest.FLASH_MODE_OFF
                )
            }
            mCameraCaptureSession!!.setRepeatingRequest(
                previewRequestBuilder!!.build(),
                null,
                childHandler
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }



    /**
     * 开始预览
     */
    private fun takePreview() {
        try {
            // 创建预览需要的CaptureRequest.Builder
            previewRequestBuilder =
                mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            // 将SurfaceView的surface作为CaptureRequest.Builder的目标
            previewRequestBuilder!!.addTarget(mSurfaceHolder!!.surface)
            // 创建CameraCaptureSession，该对象负责管理处理预览请求和拍照请求
            mCameraDevice!!.createCaptureSession(
                Arrays.asList(
                    mSurfaceHolder!!.surface,
                    mImageReader!!.surface
                ), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        if (null == mCameraDevice) {
                            return
                        }
                        // 当摄像头已经准备好时，开始显示预览
                        mCameraCaptureSession = cameraCaptureSession
                        try {
                            // 自动对焦
                            previewRequestBuilder!!.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )
                            // 显示预览
                            val previewRequest = previewRequestBuilder!!.build()
                            mCameraCaptureSession!!.setRepeatingRequest(
                                previewRequest,
                                null,
                                childHandler
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        Toast.makeText(this@MainActivity, "配置失败", Toast.LENGTH_SHORT).show()

                    }
                }, childHandler
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun front() {
        //前置时，照片竖直显示
        ORIENTATION.append(
            Surface.ROTATION_0,
            270
        )
        ORIENTATION.append(
            Surface.ROTATION_90,
            0
        )
        ORIENTATION.append(
            Surface.ROTATION_180,
            90
        )
        ORIENTATION.append(
            Surface.ROTATION_270,
            180
        )
    }

    private fun rear() {
        //后置时，照片竖直显示
        ORIENTATION.append(
            Surface.ROTATION_0,
            90
        )
        ORIENTATION.append(
            Surface.ROTATION_90,
            0
        )
        ORIENTATION.append(
            Surface.ROTATION_180,
            270
        )
        ORIENTATION.append(
            Surface.ROTATION_270,
            180
        )
    }

    /**
     * 拍照
     */
    private fun takePicture() {
        if (mCameraDevice == null) {
            return
        }
        try {
            captureRequestBuilder =
                mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            // 将imageReader的surface作为CaptureRequest.Builder的目标
            captureRequestBuilder!!.addTarget(mImageReader!!.surface)
            // 自动对焦
            captureRequestBuilder!!.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
            // 自动曝光
            captureRequestBuilder!!.set(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
            )
            //拍照
            val mCaptureRequest = captureRequestBuilder!!.build()
            mCameraCaptureSession!!.capture(mCaptureRequest, null, childHandler)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 判断是否添加了权限
     *
     * @return true
     */
    private fun hasPermissions(): Boolean {
        return EasyPermissions.hasPermissions(this, *perms)
    }

    @AfterPermissionGranted(101)
    private fun startPermissionsTask() {
        //检查是否获取该权限
        if (!hasPermissions()) {
            //权限拒绝 申请权限
            //第二个参数是被拒绝后再次申请该权限的解释
            //第三个参数是请求码
            //第四个参数是要申请的权限
            EasyPermissions.requestPermissions(
                this,
                "拍照打卡，需要拍照和读取权限",
                101,
                *perms
            )
        }
    }


    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms!!)) {
            //永久拒绝权限跳转去设置请求
            AppSettingsDialog.Builder(this)
                .setRationale("拍照打卡，需要拍照和读取权限")
                .setTitle("必需权限")
                .build()
                .show()
            return
        }
        Toast.makeText(this, "获取权限失败", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        openCamera(true)
    }


}