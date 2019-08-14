package permission;

/**
 * @author kevinliu
 */
public interface IPermissionAlwaysDenied {

    /**
     * 用户选择了拒绝并且选择不再提示
     * @return 返回true表示需要提示到系统设置去打开权限
     */
    boolean onAlwaysDeniedShowSetting();
}
