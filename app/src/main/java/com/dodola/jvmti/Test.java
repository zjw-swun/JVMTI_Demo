package com.dodola.jvmti;

import android.util.Log;

/**
 * Desc :
 * date : 2019/8/6 1:59
 *
 * @author : zhoujiawei
 */
public class Test {
    //总共就是 8字节对象头 + 4字节 int age  = 12字节

    //对象头默认占8字节

    int age = 100;
    //long money = 1000L;
   // float age = 17.5f;
   // double age = 18.0;

    public void log(){
        Log.e("TAG","Test");
    }
}
