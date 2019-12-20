package com.bcm.messenger.common.utils

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.*
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bcm.messenger.common.ARouterConstants
import com.bcm.messenger.common.provider.AmeProvider
import com.bcm.messenger.common.provider.IAmeAppModule
import com.bcm.messenger.utility.AppContextHolder
import com.bcm.messenger.utility.Base64
import com.bcm.messenger.utility.RomUtil
import com.bcm.messenger.utility.logger.ALog
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.orhanobut.logger.Logger
import org.whispersystems.libsignal.util.guava.Optional
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * AppUtil的Kotlin扩展方法和静态函数，编译后全是静态函数，不是object的单例
 *
 * Kotlin调用方式：{扩展类名称}.{函数名}，无扩展类的直接调用{函数名}
 * Java调用方式：AppUtilKotlinKt.{函数名}({扩展类参数（当扩展类存在时第一参数是扩展类的实例）}, 其他参数)
 *
 * Created by Kin on 2019/5/15
 */
private const val TAG = "AppUtilKotlin"


/**
 * 获取应用详情页面intent（如果找不到要跳转的界面，也可以先把用户引导到系统设置页面）
 *
 * @return
 */
fun Context.getAppDetailSettingIntent(): Intent {
    val localIntent = Intent()
    localIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    localIntent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
    localIntent.data = Uri.fromParts("package", packageName, null)
    return localIntent
}


/**
 * 获取某个应用包名的intent
 */
fun Context.getLaunchAppIntent(packageName: String): Intent? {
    val localIntent = packageManager.getLaunchIntentForPackage(packageName)
    localIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return localIntent
}


/**
 * 启动服务
 */
@Throws(SecurityException::class, IllegalStateException::class)
fun Context.startForegroundServiceCompat(intent: Intent): ComponentName? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        this.startForegroundService(intent)
    } else {
        this.startService(intent)
    }
}

/**
 * copy from shared perferences A to B
 */
fun SharedPreferences.copyTo(prefB: SharedPreferences) {
    val setA = this.all
    if (setA.isNotEmpty()) {
        val editorB = prefB.edit()
        for (pair in setA.entries) {
            when {
                pair.value is Boolean -> editorB.putBoolean(pair.key, pair.value as Boolean)
                pair.value is Float -> editorB.putFloat(pair.key, pair.value as Float)
                pair.value is Int -> editorB.putInt(pair.key, pair.value as Int)
                pair.value is Long -> editorB.putLong(pair.key, pair.value as Long)
                pair.value is String -> editorB.putString(pair.key, pair.value as String)
                pair.value is Set<*> -> {
                    val valueSet = pair.value as Set<String>
                    editorB.putStringSet(pair.key, valueSet)
                }
            }
        }
        editorB.apply()
    }
}

@Throws(IOException::class)
fun zipRealCompress(outputZipFile: String, compressFileList: List<String>) {
    val fout = File(outputZipFile)
    fout.createNewFile()

    val outputStream = FileOutputStream(fout)
    //清空文件
    val fileChannel = outputStream.channel
    fileChannel.truncate(0)
    fileChannel.close()

    val zipOutputStream = ZipOutputStream(FileOutputStream(fout))

    val bufferSize = 1024 * 1024
    val buffer = ByteArray(bufferSize)
    var fileInputStream: FileInputStream? = null
    try {
        for (sf in compressFileList) {
            val file = File(sf)
            if (!file.exists()) {
                continue
            }
            val filename = file.name
            val ze = ZipEntry(filename)
            zipOutputStream.putNextEntry(ze)

            fileInputStream = FileInputStream(file)
            var byteRead = fileInputStream.read(buffer, 0, bufferSize)
            while (byteRead > 0) {
                zipOutputStream.write(buffer, 0, byteRead)
                byteRead = fileInputStream.read(buffer, 0, bufferSize)
            }

            zipOutputStream.closeEntry()
            fileInputStream.close()
            fileInputStream = null
        }
    } finally {
        fileInputStream?.close()
        outputStream.close()
        zipOutputStream.close()
    }
}

/**
 * context转化成activity
 */
fun Context.toActivity(): Activity? {
    try {
        return this as? Activity ?: if (this is ContextWrapper) {
            this.baseContext.toActivity()
        } else {
            null
        }
    } catch (ex: Exception) {
        ex.printStackTrace()
    }
    return null
}

fun Context.isActivityFinished(): Boolean {
    return this.toActivity()?.isFinishing ?: true
}

fun Context.isActivityDestroyed(): Boolean {
    return this.toActivity()?.isDestroyed ?: true
}

fun View?.isActivityFinished(): Boolean {
    this ?: return true
    return context.toActivity()?.isFinishing ?: true
}

fun View?.isActivityDestroyed(): Boolean {
    this ?: return true
    return context.toActivity()?.isDestroyed ?: true
}

fun Context.getSignature(): String {
    val info = this.packageManager.getPackageInfo(this.packageName, PackageManager.GET_SIGNATURES)
    val mdg = MessageDigest.getInstance("SHA-1")
    mdg.update(info.signatures[0].toByteArray())
    return Base64.encodeBytes(mdg.digest(), 0)
}

/**
 * 获取粘贴板的内容
 */
fun Context.getTextFromBoard(): CharSequence {
    try {
        val c = this.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = c.primaryClip ?: return ""
        return if (clipData.itemCount == 0) {
            ""
        } else {
            clipData.getItemAt(0).text
        }

    } catch (ex: Exception) {
        ALog.e(TAG, "getCodeFromBoard error", ex)
    }
    return ""
}

/**
 * 保存内容至粘贴板
 * @param srcText
 */
fun Context.saveTextToBoard(srcText: String) {
    try {
        val c = this.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        c.primaryClip = ClipData.newPlainText("text", srcText)
    } catch (ex: Exception) {
        ALog.e(TAG, "saveCodeToBoard error", ex)
    }

}

/**
 * 复制uri到剪贴板
 *
 * @param uri uri
 */
fun Context.saveUriToBoard(uri: Uri) {
    try {
        val clipboard = this.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.primaryClip = ClipData.newUri(this.contentResolver, "uri", uri)
    } catch (ex: Exception) {
        ALog.e(TAG, "saveUriToBoard error", ex)
    }
}

/**
 * 获取剪贴板的uri
 *
 * @return 剪贴板的uri
 */
fun Context.getUriFromBoard(): Uri? {
    try {
        val clipboard = this.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        return if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).uri
        } else null
    } catch (ex: Exception) {
        ALog.e(TAG, "getUri error", ex)
    }
    return null
}

/**
 * 复制意图到剪贴板
 *
 * @param intent 意图
 */
fun Context.saveIntentToBoard(intent: Intent) {
    try {
        val clipboard = this.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.primaryClip = ClipData.newIntent("intent", intent)
    } catch (ex: Exception) {
        ALog.e(TAG, "saveIntentToBoard error", ex)
    }
}

/**
 * 获取剪贴板的意图
 *
 * @return 剪贴板的意图
 */
fun Context.getIntentFromBoard(): Intent? {
    try {
        val clipboard = this.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        return if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).intent
        } else null
    } catch (ex: Exception) {
        ALog.e(TAG, "getIntentFromBoard error", ex)
    }
    return null
}

/**
 * 估算一个字符串的长度
 */
fun String.measureText(): Int {
    val paint = Paint()
    val rect = Rect()
    paint.getTextBounds(this, 0, this.length, rect)
    return rect.width()
}

/**
 * 单位转换：dp转px
 * @return converted px
 */
fun Float.dp2Px(): Float {
    return this * AppContextHolder.APP_CONTEXT.resources.displayMetrics.density + 0.5f
}

fun Float.sp2Px(): Float {
    return this * AppContextHolder.APP_CONTEXT.resources.displayMetrics.scaledDensity + 0.5f
}

/**
 * 单位转换：dp转px
 * @return converted px
 */
fun Int.dp2Px(): Int {
    return (this * AppContextHolder.APP_CONTEXT.resources.displayMetrics.density + 0.5f).toInt()
}

/**
 * 单位转换：sp转px
 * @return converted px
 */
fun Int.sp2Px(): Int {
    return (this * AppContextHolder.APP_CONTEXT.resources.displayMetrics.scaledDensity + 0.5f).toInt()
}

/**
 * 单位转换：px转dp
 * @return converted dp
 */
fun Int.px2Dp(): Int {
    val scale = AppContextHolder.APP_CONTEXT.resources.displayMetrics.density
    return (this / scale + 0.5f).toInt()
}

/**
 * 单位转换：px转sp
 * @return converted sp
 */
fun Int.px2Sp(): Int {
    val fontScale = AppContextHolder.APP_CONTEXT.resources.displayMetrics.scaledDensity
    return (this / fontScale + 0.5f).toInt()
}

/**
 * 获取颜色值
 * @param resId
 */
fun Context.getColorCompat(resId: Int): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        this.resources.getColor(resId, null)
    } else {
        this.resources.getColor(resId)
    }
}

fun Fragment.getColorCompat(resId: Int): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        context?.resources?.getColor(resId, null) ?: AppContextHolder.APP_CONTEXT.getColorCompat(resId)
    } else {
        context?.resources?.getColor(resId) ?: AppContextHolder.APP_CONTEXT.getColorCompat(resId)
    }
}

fun getColor(resId: Int): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        AppContextHolder.APP_CONTEXT.resources.getColor(resId, null)
    } else {
        AppContextHolder.APP_CONTEXT.resources.getColor(resId)
    }
}

/**
 * 获取图像
 */
fun getDrawable(resId: Int): Drawable {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        AppContextHolder.APP_CONTEXT.resources.getDrawable(resId, null)
    } else {
        AppContextHolder.APP_CONTEXT.getDrawable(resId)
    }
}

fun getCircleDrawable(fillColor: Int, size: Int): Drawable {
    val gd = GradientDrawable()//创建drawable
    gd.setColor(fillColor)
    gd.shape = GradientDrawable.OVAL
    gd.setSize(size, size)
    return gd
}

fun getString(resId: Int): String {
    return AppContextHolder.APP_CONTEXT.getString(resId)
}

fun getString(resId: Int, vararg args: Any): String {
    return AppContextHolder.APP_CONTEXT.getString(resId, args)
}

/**
 * 根据传入控件的坐标和用户的焦点坐标，判断是否隐藏键盘，如果点击的位置在控件内，则不隐藏键盘
 * @param event 焦点位置
 * @param focusView 当前焦点view
 */
fun Activity.hideKeyboard(event: MotionEvent?, focusView: View?) {
    try {
        if (event == null) {
            return
        }
        if (focusView != null) {

            val location = intArrayOf(0, 0)
            focusView.getLocationInWindow(location)

            val left = location[0]
            val top = location[1]
            val right = left + focusView.width
            val bottom = top + focusView.height

            // 判断焦点位置坐标是否在空间内，如果位置在控件外，则隐藏键盘
            if (event.rawX < left || event.rawX > right || event.rawY < top || event.rawY > bottom) {
                // 隐藏键盘
                val token = focusView.windowToken
                val inputMethodManager = this.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                inputMethodManager?.hideSoftInputFromWindow(token, InputMethodManager.HIDE_NOT_ALWAYS)
            }
        }

    } catch (ex: Exception) {
        ex.printStackTrace()
    }
}

/**
 * 隐藏软键盘
 */
fun Activity.hideKeyboard() {
    val imm = this.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.hideSoftInputFromWindow(this.window.decorView.windowToken, 0)
}

fun View.hideKeyboard() {
    val imm = this.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.hideSoftInputFromWindow(this.windowToken, 0)
}

/**
 * 显示软键盘
 */
fun View.showKeyboard() {
    val imm = this.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.showSoftInput(this, InputMethodManager.SHOW_FORCED)
}

/**
 * Gets the content:// URI from the given corresponding path to a file
 * 绝对路径转uri
 *
 * @param filePath
 * @return content Uri
 */
fun Context.getImageContentUri(filePath: String): Uri {
    var cursor: Cursor? = null
    try {
        cursor = this.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID), MediaStore.Images.Media.DATA + "=? ",
                arrayOf(filePath), null)
        return if (cursor != null && cursor.moveToFirst()) {
            val id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID))
            Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
        } else {
            val values = ContentValues()
            values.put(MediaStore.Images.Media.DATA, filePath)
            this.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }
    } finally {
        cursor?.close()
    }
}

/**
 * uri转绝对路径
 */
fun Context.getImagePathFromURI(contentURI: Uri): String {
    val result: String
    val cursor = if (contentURI.toString().startsWith(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString())) {
        this.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.ImageColumns.DATA),
                null, null,
                MediaStore.Images.ImageColumns.DATE_ADDED + " desc limit 1"
        )
    } else {
        // 数据改变时查询数据库中最后加入的一条数据
        this.contentResolver.query(
                MediaStore.Images.Media.INTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.ImageColumns.DATA), null, null,
                MediaStore.Images.ImageColumns.DATE_ADDED + " desc limit 1"
        )
    }
    result = if (cursor == null)
        contentURI.path
    else {
        cursor.moveToFirst()
        val index = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
        cursor.getString(index) ?: ""
    }
    cursor?.close()
    return result
}


/**
 * 检测悬浮窗权限
 * @return
 */
fun Context.checkOverlaysPermission(): Boolean {
    try {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.SYSTEM_ALERT_WINDOW) == PackageManager.PERMISSION_GRANTED

        }
    } catch (ex: Exception) {
        ALog.e(TAG, "checkOverlaysPermission error", ex)
    }

    return false
}

/**
 * 请求悬浮窗权限
 * @param requestCode
 * @return true表示执行跳转，false表示没有
 */
fun Fragment.requestOverlaysPermission(requestCode: Int): Boolean {
    try {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //如果不支持浮窗，需要授权
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + this.context?.packageName))
            this.startActivityForResult(intent, requestCode)
            true
        } else {
            if (ContextCompat.checkSelfPermission(this.context!!, Manifest.permission.SYSTEM_ALERT_WINDOW) != PackageManager.PERMISSION_GRANTED) {
                this.requestPermissions(arrayOf(Manifest.permission.SYSTEM_ALERT_WINDOW), requestCode)
            }
            true
        }
    } catch (ex: Exception) {
        ALog.e(TAG, "requestOverlaysPermission error", ex)
    }

    return false
}

/**
 * 请求悬浮窗权限
 * @param requestCode
 * @return true表示执行跳转，false表示没有
 */
fun Activity.requestOverlaysPermission(requestCode: Int): Boolean {
    try {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //如果不支持浮窗，需要授权
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${this.packageName}"))
            this.startActivityForResult(intent, requestCode)
            true
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SYSTEM_ALERT_WINDOW) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SYSTEM_ALERT_WINDOW), requestCode)
            }
            true
        }
    } catch (ex: Exception) {
        ALog.e(TAG, "requestOverlaysPermission error", ex)
    }

    return false
}

fun Context.getPackageInfo(): PackageInfo {
    var info: PackageInfo? = null
    try {
        info = this.packageManager.getPackageInfo(this.packageName, 0)
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace(System.err)
    }

    if (info == null) info = PackageInfo()
    return info
}

/**
 * 设置状态栏透明
 */
fun Window.setTranslucentStatus() {
    // 5.0以上系统状态栏透明
    when {
        RomUtil.isMiui() || RomUtil.isFlyme() -> {
            this.setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS, WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        }
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1 -> return
        else -> {
            this.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            this.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            this.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            this.statusBarColor = Color.TRANSPARENT
        }
    }
}

/**
 * 修改状态栏的沉浸样式
 * @param dark 状态栏文本颜色
 */
fun Window.setTransparencyBar(dark: Boolean = true) {
    if (dark) {
        setStatusBarLightMode()
    } else {
        setStatusBarDarkMode()
    }
}

const val STATUS_BAR_UNKNOWN = 0
const val STATUS_BAR_MIUI = 1
const val STATUS_BAR_FLYME = 2
const val STATUS_BAR_MARSHMALLOW = 3

/**
 * 设置状态栏黑色字体图标，
 * 适配4.4以上版本MIUI、Flyme和6.0以上版本其他Android
 *
 * @return 1:MIUI 2:Flyme 3:android6.0
 */
fun Window.setStatusBarLightMode(): Int {
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
        return STATUS_BAR_UNKNOWN
    }
    this.setTranslucentStatus()
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
            this.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            STATUS_BAR_MARSHMALLOW
        }
        setStatusBarLightModeForMIUI(true) -> STATUS_BAR_MIUI
        setStatusBarLightModeForFlyme(true) -> STATUS_BAR_FLYME
        else -> STATUS_BAR_UNKNOWN
    }
}

fun Window.setStatusBarDarkMode(): Int {
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
        return STATUS_BAR_UNKNOWN
    }
    this.setTranslucentStatus()
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
            this.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_VISIBLE
            STATUS_BAR_MARSHMALLOW
        }
        setStatusBarLightModeForMIUI(false) -> STATUS_BAR_MIUI
        setStatusBarLightModeForFlyme(false) -> STATUS_BAR_FLYME
        else -> STATUS_BAR_UNKNOWN
    }
}

/**
 * 已知系统类型时，设置状态栏黑色字体图标。
 * 适配4.4以上版本MIUI、Flyme和6.0以上版本其他Android
 *
 * @param type   1:MIUI 2:Flyme 3:android6.0
 */
fun Window.setStatusBarLightMode(type: Int) {
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
        return
    }
    setTranslucentStatus()
    when (type) {
        STATUS_BAR_MIUI -> setStatusBarLightModeForMIUI(true)
        STATUS_BAR_FLYME -> setStatusBarLightModeForFlyme(true)
        STATUS_BAR_MARSHMALLOW -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                this.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
    }

}

/**
 * 清除MIUI或Flyme或6.0以上版本状态栏黑色字体
 */
fun Window.setStatusBarDarkMode(type: Int) {
    setTranslucentStatus()
    when (type) {
        STATUS_BAR_MIUI -> setStatusBarLightModeForMIUI(false)
        STATUS_BAR_FLYME -> setStatusBarLightModeForFlyme(false)
        STATUS_BAR_MARSHMALLOW -> this.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_VISIBLE
    }

}


/**
 * 设置状态栏图标为深色和魅族特定的文字风格
 * 可以用来判断是否为Flyme用户
 *
 * @param dark   是否把状态栏字体及图标颜色设置为深色
 * @return boolean 成功执行返回true
 */
private fun Window.setStatusBarLightModeForFlyme(dark: Boolean): Boolean {
    var result = false
    try {
        val lp = this.attributes
        val darkFlag = WindowManager.LayoutParams::class.java
                .getDeclaredField("MEIZU_FLAG_DARK_STATUS_BAR_ICON")
        val meizuFlags = WindowManager.LayoutParams::class.java
                .getDeclaredField("meizuFlags")
        darkFlag.isAccessible = true
        meizuFlags.isAccessible = true
        val bit = darkFlag.getInt(null)
        var value = meizuFlags.getInt(lp)
        value = if (dark) {
            value or bit
        } else {
            value and bit.inv()
        }
        meizuFlags.setInt(lp, value)
        this.attributes = lp
        result = true
    } catch (e: Exception) {
    }
    return result
}

/**
 * 设置状态栏字体图标为深色，需要MIUI V6以上
 *
 * @param dark   是否把状态栏字体及图标颜色设置为深色
 * @return boolean 成功执行返回true
 */
private fun Window.setStatusBarLightModeForMIUI(dark: Boolean): Boolean {
    var result = false
    val clazz = this.javaClass
    try {
        var darkModeFlag = 0
        val layoutParams = Class.forName("android.view.MiuiWindowManager\$LayoutParams")
        val field = layoutParams.getField("EXTRA_FLAG_STATUS_BAR_DARK_MODE")
        darkModeFlag = field.getInt(layoutParams)
        val extraFlagField = clazz.getMethod("setExtraFlags", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        if (dark) {
            extraFlagField.invoke(this, darkModeFlag, darkModeFlag)//状态栏透明且黑色字体
        } else {
            extraFlagField.invoke(this, 0, darkModeFlag)//清除黑色字体
        }
        result = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //开发版 7.7.13 及以后版本采用了系统API，旧方法无效但不会报错，所以两个方式都要加上
            if (dark) {
                this.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                this.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    } catch (e: Exception) {
    }
    return result
}

/**
 * 获取屏幕大小
 * @return
 */
fun Context.getScreenPixelSize(): IntArray {
    val metrics = this.resources.displayMetrics
    return intArrayOf(metrics.widthPixels, metrics.heightPixels)
}

/**
 * 获取屏幕宽度
 * @return Screen width pixels
 */
fun Context.getScreenWidth(): Int {
    return this.resources.displayMetrics.widthPixels
}

/**
 * 获取屏幕高度，不含状态栏和导航栏
 * @return Screen height pixels, exclude status bar and navigation bar
 */
fun Context.getScreenHeight(): Int {
    return this.resources.displayMetrics.heightPixels
}

/**
 * 获取屏幕真实宽度
 * @return Screen width pixels
 */
fun getRealScreenWidth(): Int {
    val manager = AppContextHolder.APP_CONTEXT.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val metrics = DisplayMetrics()
    manager.defaultDisplay.getRealMetrics(metrics)
    return metrics.widthPixels
}

/**
 * 获取屏幕真实高度，包含状态栏和导航栏
 * @return Screen height pixels, include status bar and navigation bar
 */
fun getRealScreenHeight(): Int {
    val manager = AppContextHolder.APP_CONTEXT.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val metrics = DisplayMetrics()
    manager.defaultDisplay.getRealMetrics(metrics)
    return metrics.heightPixels
}

fun Context.getStatusBarHeight(): Int {
    val resourceId = this.resources.getIdentifier("status_bar_height", "dimen", "android")
    return this.resources.getDimensionPixelSize(resourceId)
}

fun Context.getNavigationBarHeight(): Int {
    val resourceId = this.resources.getIdentifier("navigation_bar_height", "dimen", "android")
    return this.resources.getDimensionPixelSize(resourceId)
}

fun Context.checkDeviceHasNavigationBar(): Boolean {
    var hasNavigationBar = false
    val rs = this.resources
    val id = rs.getIdentifier("config_showNavigationBar", "bool", "android")
    if (id > 0) {
        hasNavigationBar = rs.getBoolean(id)
    }
    try {
        val systemPropertiesClass = Class.forName("android.os.SystemProperties")
        val m = systemPropertiesClass.getMethod("get", String::class.java)
        val navBarOverride = m.invoke(systemPropertiesClass, "qemu.hw.mainkeys") as String
        if ("1" == navBarOverride) {
            hasNavigationBar = false
        } else if ("0" == navBarOverride) {
            hasNavigationBar = true
        }
    } catch (e: Exception) {

    }

    return hasNavigationBar
}

/**
 * 获取当前栈顶activity， 5.x
 * @return
 */
fun Context.getCurrentPackageName(): String? {
    val startTaskToFront = 2
    var field: Field? = null
    try {
        field = ActivityManager.RunningAppProcessInfo::class.java.getDeclaredField("processState")//通过反射获取进程状态字段.
    } catch (e: Exception) {
        e.printStackTrace()
    }

    val manager = this.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val runningAppProcesses = manager.runningAppProcesses
    var currentInfo: ActivityManager.RunningAppProcessInfo? = null
    for (i in runningAppProcesses.indices) {
        val process = runningAppProcesses[i]
        if (process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
            // 前台运行进程
            var state: Int? = null
            try {
                state = field!!.getInt(process)//反射调用字段值的方法,获取该进程的状态.
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (state != null && state == startTaskToFront) {
                currentInfo = process
                break
            }
        }
    }
    var pkgName: String? = null
    if (currentInfo != null) {
        pkgName = currentInfo.processName
    }
    return pkgName
}

fun generateViewId(): Int {
    return View.generateViewId()
}

/**
 * 实现高斯模糊
 * @param context
 * @param blurRadius 实现高斯模糊度
 */
fun Bitmap.blurBitmap(context: Context, blurRadius: Float): Bitmap {

    //Let's create an empty bitmap with the same size of the bitmap we want to blur
    val outBitmap = Bitmap.createBitmap(this.width, this.height, Bitmap.Config.ARGB_8888)

    //Instantiate a new Renderscript
    val rs = RenderScript.create(context)

    //Create an Intrinsic Blur Script using the Renderscript
    val blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))

    //Create the Allocations (in/out) with the Renderscript and the in/out bitmaps
    val allIn = Allocation.createFromBitmap(rs, this)
    val allOut = Allocation.createFromBitmap(rs, outBitmap)

    //Set the radius of the blur: 0 < radius <= 25
    blurScript.setRadius(blurRadius)

    //Perform the Renderscript
    blurScript.setInput(allIn)
    blurScript.forEach(allOut)

    //Copy the final bitmap created by the out Allocation to the outBitmap
    allOut.copyTo(outBitmap)

    //recycle the original bitmap
    this.recycle()

    //After finishing everything, we destroy the Renderscript.
    rs.destroy()

    return outBitmap
}


/**
 * 实现高斯模糊
 * @param blurRadius 实现高斯模糊度
 */
fun ImageView.blurBitmap(url: String?, blurRadius: Float) {
    if (null == url || null == this.context) {
        return
    }

    if (url == this.tag) {
        return
    }

    this.tag = url
    try {
        val weakView = WeakReference<ImageView>(this)
        val weakContext = WeakReference<Context>(this.context)
        Glide.with(this.context.applicationContext)
                .asBitmap()
                .load(url)
                .into(object : SimpleTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        Logger.e("blurImage", "onResourceReady")
                        applyBitmap(resource)
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        Logger.e("blurImage", "onLoadFailed")
                    }

                    private fun applyBitmap(bitmap: Bitmap) {
                        val newBitmap = Bitmap.createBitmap(bitmap)
                        val v: ImageView? = weakView.get()
                        val context: Context? = weakContext.get()
                        if (null != v && null != context && weakView.get()?.tag == url) {
                            v.setImageBitmap(newBitmap.blurBitmap(context, blurRadius))
                        }
                    }

                })
    } catch (ex: Exception) {
        ALog.e(TAG, "ChatRtcCallScreen 显示图片失败", ex)
    }
}

/**
 * 返回 app的名字
 */
fun Context.getApplicationName(): String {
    val applicationInfo = this.applicationInfo
    val stringId = applicationInfo.labelRes

    return if (stringId == 0) {
        applicationInfo.nonLocalizedLabel.toString()
    } else {
        this.getString(stringId)
    }
}

/**
 * 返回 app的version code
 */
fun Context.getVersionCode(): Int {
    return this.getPackageInfo().versionCode
}

/**
 * 返回 app的version name
 */
fun Context.getVersionName(): String {
    return this.getPackageInfo().versionName
}


/**
 * true 开发版本
 */
fun isDevBuild(): Boolean {
    val provider = AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)
    return provider?.isDevBuild() ?: false
}

/**
 * true 公测版本
 */
fun isBetaBuild(): Boolean {
    val provider = AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)
    return provider?.isBetaBuild() ?: false
}

/**
 * true 发布版本
 */
fun isReleaseBuild(): Boolean {
    val provider = AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)
    return provider?.isReleaseBuild() ?: true
}

fun isGooglePlayEdition(): Boolean {
    val provider = AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)
    return provider?.isGooglePlayEdition() ?: true
}

/**
 * true 发布版本(发布到google play 平台的版本)
 */
fun isSupportGooglePlay(): Boolean {
    val provider = AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)
    return provider?.isSupportGooglePlay() ?: true
}

/**
 * true lbs激活
 */
fun isLbsEnable(): Boolean {
    val provider = AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)
    return provider?.lbsEnable() ?: true
}

/**
 * 返回版本构建时间
 */
fun lastBuildTime(): Long {
    val provider = AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)
    return provider?.lastBuildTime() ?: 0
}

/**
 * true 开启测试环境，false 是生产环境
 */
fun isTestEnvEnable(): Boolean {
    val provider = AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)
    return provider?.testEnvEnable() ?: false
}

/**
 * 返回是否采用开发者链路
 */
fun useDevBlockChain(): Boolean {
    val provider = AmeProvider.get<IAmeAppModule>(ARouterConstants.Provider.PROVIDER_APPLICATION_BASE)
    return provider?.useDevBlockChain() ?: false

}

fun Context.is24HourFormat(): Boolean {
    return android.text.format.DateFormat.is24HourFormat(this)
}

fun Context.isDefaultSmsProvider(): Boolean {
    return this.packageName == Telephony.Sms.getDefaultSmsPackage(this)
}

fun getSimCountryIso(): Optional<String> {
    val simCountryIso = (AppContextHolder.APP_CONTEXT.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).simCountryIso
    return Optional.fromNullable(simCountryIso?.toUpperCase())
}

/**
 * 检测是否不可用地址, IPv4
 * @return
 */
fun String.checkInvalidAddressV4(): Boolean {
    try {

        val addressStrings = this.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val address = ByteArray(addressStrings.size)
        for (i in address.indices) {
            address[i] = java.lang.Byte.valueOf(addressStrings[i])
        }
        if (address.size == 4) {
            val b0 = address[0]
            val b1 = address[1]
            //127.x.x.x
            val SECTION_0 = 0x7F.toByte()
            //10.x.x.x/8
            val SECTION_1 = 0x0A.toByte()
            //172.16.x.x/12--172.31.x.x
            val SECTION_2 = 0xAC.toByte()
            val SECTION_3 = 0x10.toByte()
            val SECTION_4 = 0x1F.toByte()
            //192.168.x.x/16
            val SECTION_5 = 0xC0.toByte()
            val SECTION_6 = 0xA8.toByte()
            when (b0) {
                SECTION_0 -> return true
                SECTION_1 -> return true
                SECTION_2 -> if (b1 in SECTION_3..SECTION_4) {
                    return true
                }
                SECTION_5 -> if (b1 == SECTION_6) {
                    return true
                }
                else -> return false
            }
        }
    } catch (ex: Exception) {
        ALog.e(TAG, "checkInvalidAddressV4 error", ex)
    }

    return false
}

/**
 * return true 在主进程
 */
fun isMainProcess(): Boolean {
    val am = AppContextHolder.APP_CONTEXT.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val processInfos = am?.getRunningAppProcesses()?.toList()
    val mainProcessName = AppContextHolder.APP_CONTEXT.packageName;
    val myPid = Process.myPid()
    if (null != processInfos) {
        for (info in processInfos) {
            if (info.pid == myPid && mainProcessName.equals(info.processName)) {
                return true
            }
        }
    }
    return false
}

fun exitApp() {
    try {
        val activityManager = AppContextHolder.APP_CONTEXT.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.appTasks.forEach {
            it.finishAndRemoveTask()
        }
        System.exit(0)
    } catch (e: java.lang.Exception) {
        e.printStackTrace()
    }
}

/**
 * 生成Activity的截图
 */
fun Activity.createScreenShot(): Bitmap {
    val view = this.window.decorView
    view.isDrawingCacheEnabled = true
    view.buildDrawingCache()
    val bitmap = Bitmap.createBitmap(view.drawingCache, 0, 0, view.measuredWidth, view.measuredHeight)
    view.isDrawingCacheEnabled = false
    view.destroyDrawingCache()
    return bitmap
}

/**
 * 生成View的截图
 */
fun View.createScreenShot(): Bitmap {
    this.isDrawingCacheEnabled = true
    this.buildDrawingCache()
    this.measure(View.MeasureSpec.makeMeasureSpec(this.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(this.height, View.MeasureSpec.EXACTLY))
    this.layout(this.x.toInt(), this.y.toInt(), this.x.toInt() + this.measuredWidth, this.y.toInt() + this.measuredHeight)
    val bitmap = Bitmap.createBitmap(this.drawingCache, 0, 0, this.measuredWidth, this.measuredHeight)
    this.isDrawingCacheEnabled = false
    this.destroyDrawingCache()
    return bitmap
}

const val TYPE_5G = 5
const val TYPE_4G = 4
const val TYPE_3G = 3
const val TYPE_2G = 2
const val TYPE_WIFI = 1
const val TYPE_UNKNOWN = 0

fun isUsingNetwork(): Boolean {
    try {
        val manager = AppContextHolder.APP_CONTEXT.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val info = manager.activeNetworkInfo
        return info != null && info.isConnectedOrConnecting
    } catch (ex: Exception) {

    }
    return true
}

fun Context.isNetworkAvailable(): Boolean {
    try {
        val manager = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val info = manager.activeNetworkInfo
        return info != null && info.isAvailable
    } catch (ex: Exception) {}
    return true
}

fun Context.isWebProcess(): Boolean {
    try {
        val manager = this.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processName = "$packageName:web"
        val pid = Process.myPid()
        manager.runningAppProcesses.forEach {
            if (it.pid == pid && it.processName == processName) {
                return true
            }
        }
    } catch (tr: Throwable) {}
    return false
}