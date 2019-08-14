package permission;

import android.content.Context;

import com.yanzhenjie.permission.Action;
import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.Rationale;
import com.yanzhenjie.permission.RequestExecutor;

import java.util.List;

/**
 *
 * @author KevinLiu
 * @date 2018/1/31
 */

public class PermissionManager {

    /**
     * 获取权限
     * @param context
     * @param iPermissionSuccess  获取成功
     * @param iPermissionFailed  被拒绝
     * @param iPermissionAlwaysDenied  被拒绝后禁止再提示   返回true 则显示系统设置
     * @param permissions
     */
    public static void grantPermission(final Context context, final IPermissionSuccess iPermissionSuccess, final IPermissionFailed iPermissionFailed, final IPermissionAlwaysDenied iPermissionAlwaysDenied, final String... permissions) {
        AndPermission.with(context)
                .runtime()
                .permission(permissions)
                .rationale(new Rationale<List<String>>() {
                    @Override
                    public void showRationale(Context context, List<String> data, RequestExecutor executor) {
                        executor.execute();
                    }
                })
                .onGranted(new Action() {
                    @Override
                    public void onAction(Object data) {
                        if (iPermissionSuccess != null) {
                            iPermissionSuccess.onSuccess();
                        }
                    }

                }).onDenied(new Action<List<String>>() {
            @Override
            public void onAction(List<String> data) {
                if (AndPermission.hasAlwaysDeniedPermission(context, permissions)) {
                    // 打开权限设置页
                    if (iPermissionAlwaysDenied != null){
                        if (iPermissionAlwaysDenied.onAlwaysDeniedShowSetting()){
                            AndPermission.permissionSetting(context).execute();
                            return;
                        }
                    }
                }

                if (iPermissionFailed != null) {
                    iPermissionFailed.onFailed();
                }

            }
        }).start();
    }


    /**
     * 获取权限，不处理拒绝后不再询问的状况
     * @param context
     * @param iPermissionSuccess
     * @param iPermissionFailed
     * @param permissions
     */
    public static void grantPermission(final Context context, final IPermissionSuccess iPermissionSuccess, final IPermissionFailed iPermissionFailed, final String... permissions) {
        grantPermission(context,iPermissionSuccess,iPermissionFailed,null,permissions);
    }

    /**
     * 获取权限，不处理拒绝的情况
     * @param context
     * @param iPermissionSuccess
     * @param permissions
     */
    public static void grantPermission(final Context context, final IPermissionSuccess iPermissionSuccess, final String... permissions) {
        grantPermission(context,iPermissionSuccess,null,null,permissions);
    }
}
