package com.dodola.jvmti;

import android.content.Context;
import android.os.Environment;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;

import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;

public class HotFix {

  public static final String DEX_OPT_DIR = "optimize_dex";//dex的优化路径
  public static final String DEX_BASECLASSLOADER_CLASS_NAME = "dalvik.system.BaseDexClassLoader";
  public static final String DEX_FILE_E = "dex";//扩展名
  public static final String DEX_ELEMENTS_FIELD = "dexElements";//pathList中的dexElements字段
  public static final String DEX_PATHLIST_FIELD = "pathList";//BaseClassLoader中的pathList字段
  public static final String FIX_DEX_PATH = "fix_dex";//fixDex存储的路径

/**
 * 获得pathList中的dexElements
 *
 * @param obj
 * @return
 * @throws NoSuchFieldException
 * @throws IllegalAccessException
 */
public Object getDexElements(Object obj) throws NoSuchFieldException, IllegalAccessException {
    return getField(obj, obj.getClass(), DEX_ELEMENTS_FIELD);
}

public interface LoadDexFileInterruptCallback {
    boolean loadDexFile(File file);
}
/**
 * fix
 *
 * @param context
 */
public void loadDex(Context context, File dexFile) {
    if (context == null) {
        return;
    }
    File fixDir = context.getDir(FIX_DEX_PATH, Context.MODE_PRIVATE);
    //mrege and fix
    mergeDex(context, fixDir,dexFile);
}

/**
 * 获取指定classloader 中的pathList字段的值（DexPathList）
 *
 * @param classLoader
 * @return
 */
public Object getDexPathListField(Object classLoader) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
    return getField(classLoader, Class.forName(DEX_BASECLASSLOADER_CLASS_NAME), DEX_PATHLIST_FIELD);
}

/**
 * 获取一个字段的值
 *
 * @return
 */
public Object getField(Object obj, Class<?> clz, String fieldName) throws NoSuchFieldException, IllegalAccessException {

    Field field = clz.getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(obj);

}

/**
 * 为指定对象中的字段重新赋值
 *
 * @param obj
 * @param claz
 * @param filed
 * @param value
 */
public void setFiledValue(Object obj, Class<?> claz, String filed, Object value) throws NoSuchFieldException, IllegalAccessException {
    Field field = claz.getDeclaredField(filed);
    field.setAccessible(true);
    field.set(obj, value);
 //        field.setAccessible(false);
}

/**
 * 合并dex
 *
 * @param context
 * @param fixDexPath
 */
public void mergeDex(Context context, File fixDexPath, File dexFile) {
    try {
        //创建dex的optimize路径
        File optimizeDir = new File(fixDexPath.getAbsolutePath(), DEX_OPT_DIR);
        if (!optimizeDir.exists()) {
            optimizeDir.mkdir();
        }
        //加载自身Apk的dex，通过PathClassLoader
        PathClassLoader pathClassLoader = (PathClassLoader) context.getClassLoader();
        //找到dex并通过DexClassLoader去加载
        //dex文件路径，优化输出路径，null,父加载器
        DexClassLoader dexClassLoader = new DexClassLoader(dexFile.getAbsolutePath(), optimizeDir.getAbsolutePath(),        null, pathClassLoader);
        //获取app自身的BaseDexClassLoader中的pathList字段
        Object appDexPathList = getDexPathListField(pathClassLoader);
        //获取补丁的BaseDexClassLoader中的pathList字段
        Object fixDexPathList = getDexPathListField(dexClassLoader);

        Object appDexElements = getDexElements(appDexPathList);
        Object fixDexElements = getDexElements(fixDexPathList);
        //合并两个elements的数据，将修复的dex插入到数组最前面
        Object finalElements = combineArray(fixDexElements, appDexElements);
        //给app 中的dex pathList 中的dexElements 重新赋值
        setFiledValue(appDexPathList, appDexPathList.getClass(), DEX_ELEMENTS_FIELD, finalElements);
        Toast.makeText(context, "修复成功!", Toast.LENGTH_SHORT).show();

    } catch (Exception e) {
        e.printStackTrace();
    }
}

/**
 * 两个数组合并
 *
 * @param arrayLhs
 * @param arrayRhs
 * @return
 */
private static Object combineArray(Object arrayLhs, Object arrayRhs) {
    Class<?> localClass = arrayLhs.getClass().getComponentType();
    int i = Array.getLength(arrayLhs);
    int j = i + Array.getLength(arrayRhs);
    Object result = Array.newInstance(localClass, j);
    for (int k = 0; k < j; ++k) {
        if (k < i) {
            Array.set(result, k, Array.get(arrayLhs, k));
        } else {
            Array.set(result, k, Array.get(arrayRhs, k - i));
        }
    }
    return result;
}

/**
 * 复制SD卡中的补丁文件到dex目录
 */
public static void copyDexFileToAppAndFix(Context context, String dexFileName, boolean copyAndFix) {
    File path = new File(Environment.getExternalStorageDirectory(), dexFileName);
    if (!path.exists()) {
        Toast.makeText(context, "没有找到补丁文件", Toast.LENGTH_SHORT).show();
        return;
    }
    if (!path.getAbsolutePath().endsWith(DEX_FILE_E)){
        Toast.makeText(context, "补丁文件格式不正确", Toast.LENGTH_SHORT).show();
        return;
    }
    File dexFilePath = context.getDir(FIX_DEX_PATH, Context.MODE_PRIVATE);
    File dexFile = new File(dexFilePath, dexFileName);
    if (dexFile.exists()) {
        dexFile.delete();
    }
    //copy
    InputStream is = null;
    FileOutputStream os = null;
    try {
        is = new FileInputStream(path);
        os = new FileOutputStream(dexFile);
        int len = 0;
        byte[] buffer = new byte[1024];
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
        if (dexFile.exists() && copyAndFix) {
            //复制成功,进行修复
            new HotFix().loadDex(context, dexFile);
        }
        path.delete();//删除sdcard中的补丁文件，或者你可以直接下载到app的路径中
        is.close();
        os.close();
    } catch (Exception e) {
        e.printStackTrace();
    } finally {
        if (is != null) {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (os != null) {
            try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
   }