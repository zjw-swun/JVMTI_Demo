@file:Suppress("UNREACHABLE_CODE")

package com.dodola.jvmti

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.dodola.jvmtilib.JVMTIHelper
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.reflect.Modifier
import java.util.*


class MainActivity : Activity() {

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sample_text.setOnClickListener { JVMTIHelper.init(this@MainActivity) }
        button_gc.setOnClickListener {
            System.gc()
            System.runFinalization()
        }
        button_modify_class.setOnClickListener { JVMTIHelper.retransformClasses(arrayOf(Activity::class.java)) }
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
            val size = fullSizeOf(obj)
            //对象头的大小是8个字节
            getObjectSizeBt.text = "对象大小为：$size"
        }

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

