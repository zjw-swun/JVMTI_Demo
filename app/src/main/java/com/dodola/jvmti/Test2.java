package com.dodola.jvmti;

/**
 * Desc :
 * date : 2019/8/6 2:09
 *
 * @author : zhoujiawei
 */
public class Test2 {
    //总共就是 8字节对象头 + 4字节 int age + 4字节引用类型占位 = 16字节
    int age = 100;
    //每个引用类型占用 4 bytes
    Test mTest =  new Test();
}
