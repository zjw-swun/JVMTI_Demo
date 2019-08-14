@file:Suppress("UNREACHABLE_CODE")

package com.dodola.jvmti

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.dodola.jvmtilib.JVMTIHelper
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.reflect.Modifier
import java.util.*
import dalvik.system.DexClassLoader
import permission.IPermissionSuccess
import permission.PermissionManager.grantPermission
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.lang.Exception


class MainActivity : Activity() {

    private lateinit var dexClassLoader: DexClassLoader
    private var libProvierClazz: Class<*>? = null
    val TAG = "MainActivityClassLoader"
    val SHOWSTRINGCLASS = "out.jar"
    val SHOWSTRINGCLASS_PATH = "com.dodola.jvmti.Test"
    val DEX = "dex"


    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //获取权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            grantPermission(
                this,
                IPermissionSuccess { },
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

        sample_text.setOnClickListener { JVMTIHelper.init(this@MainActivity) }
        button_gc.setOnClickListener {
            System.gc()
            System.runFinalization()
        }
        button_modify_class.setOnClickListener {
            // redefineClass：对于已经加载的类重新进行转换处理，即会触发重新加载类定义，
            // 需要注意的是，新加载的类不能修改旧有的类声明，譬如不能增加属性、不能修改方法声明
            Test().log()
            // HotFix.copyDexFileToAppAndFix(this@MainActivity, "out.dex", true)

            JVMTIHelper.init(this@MainActivity)
            //dexbyte
            val dexbyte = getBytes(assets.open("out.dex"))
            JVMTIHelper.redefineClass(Test::class.java, dexbyte)
            Test().log()
        }
        button_start_activity.setOnClickListener {
            startActivity(
                Intent(
                    this@MainActivity,
                    Main2Activity::class.java
                )
            )
        }
        val arrayListOf = arrayListOf<String>()
        getObjectSizeBt.setOnClickListener {
            JVMTIHelper.init(this@MainActivity)
            var obj = Test2()
            //对象头(8字节 这个可以用unsafe计算偏移量得出) + 基本类型(boolean、byte 占用 1 byte，char、short 占用 2 bytes，int、float 占用 4 bytes，long、double 占用 8 bytes) + 引用类型(占用 4 bytes)

            //计算对象大小 不包括object成员变量实际大小
            //JVMTIHelper.getObjectSize(obj)

            //深度遍历计算大小 28 = Test2 占字节数（8字节对象头 + 4字节 int age +  4字节Test引用类型占位 = 16字节）  + Test  占字节数（8字节对象头 + 4字节 int age  = 12字节)
            // 16+12 =28
            val size = fullSizeOf(obj)
            //对象头的大小是8个字节
            getObjectSizeBt.text = "对象大小为：$size"
        }
    }

    //将文件转换成Byte数组
    fun getBytes(input: InputStream): ByteArray? {
        val outputStream = ByteArrayOutputStream()
        try {
            val buffer = ByteArray(1024 * 4)
            var n = 0
            while (-1 != n) {
                n = input.read(buffer)
                if (-1 != n) {
                    outputStream.write(buffer, 0, n)
                }
            }
            return outputStream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            input.close()
            outputStream.close()
        }
        return null
    }

    /**
     * Calculates full size of object iterating over
     * its hierarchy graph.
     * @param obj object to calculate size of
     * @return object size
     */
    fun fullSizeOf(obj: Any): Long {
        val visited = IdentityHashMap<Any, Any>()
        val stack = Stack<Any>()

        var result = internalSizeOf(obj, stack, visited)
        while (!stack.isEmpty()) {
            result += internalSizeOf(stack.pop(), stack, visited)
        }
        visited.clear()
        return result
    }

    private fun skipObject(obj: Any?, visited: Map<Any?, Any?>): Boolean {
        if (obj is String) {//这个if是bug，应当去掉--teasp
            // skip interned string
            if (obj === obj.intern()) {
                return true
            }
        }
        return obj == null || visited.containsKey(obj)
    }

    private fun internalSizeOf(obj: Any, stack: Stack<Any>, visited: MutableMap<Any?, Any?>): Long {
        if (skipObject(obj, visited)) {
            return 0
        }
        visited[obj] = null

        var result: Long = 0
        // get size of object + primitive variables + member pointers
        result += JVMTIHelper.getObjectSize(obj)

        // process all array elements
        var clazz: Class<*>? = obj.javaClass
        if (clazz!!.isArray) {
            if (clazz.name.length != 2) {// skip primitive type array
                val length = java.lang.reflect.Array.getLength(obj)
                for (i in 0 until length) {
                    stack.add(java.lang.reflect.Array.get(obj, i))
                }
            }
            return result
        }

        // process all fields of the object
        while (clazz != null) {
            val fields = clazz.declaredFields
            for (i in fields.indices) {
                if (!Modifier.isStatic(fields[i].modifiers)) {
                    if (fields[i].type.isPrimitive) {
                        continue // skip primitive fields
                    } else {
                        fields[i].isAccessible = true
                        try {
                            // objects to be estimated are put to stack
                            val objectToAdd = fields[i].get(obj)
                            if (objectToAdd != null) {
                                stack.add(objectToAdd)
                            }
                        } catch (ex: IllegalAccessException) {
                            assert(false)
                        }

                    }
                }
            }
            clazz = clazz.superclass
        }
        return result
    }


}

