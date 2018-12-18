package tacoball.com.geomancer;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.util.Log;

import org.apache.commons.io.FileUtils;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.reader.MapFile;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * 共用程式
 */
public class MainUtils {

    // 除錯標籤
    private static final String TAG = "MainUtils";

    // 各偏好設定 KEY 值
    private static final String PREFKEY_UPDATE_BY_MOBILE = "UPDATE_FROM_MOBILE"; // 允許行動網路更新

    // 地圖檔名
    public static final String MAP_NAME = "taiwan-taco.map";

    // 資料庫檔名
    public static final String UNLUCKY_HOUSE = "unluckyhouse.sqlite";

    // 前端狀態事件的分類名稱
    private static final String INTENT_CATEGORY = "tacoball.com.geomancer.FrontEndState";

    // 更新伺服器
    public static final int       MIRROR_NUM = 0;
    private static final String[] MIRROR_SITES = {
        "mirror.ossplanet.net",  // Mirror
        "sto.tacosync.com",      // Web 1
        "tacosync.com",          // Web 2
        "192.168.1.81",          // WiFi LAN 1 (Debug)
        "192.168.1.172",         // WiFi LAN 2 (Debug)
        "192.168.42.29"          // USB LAN (Debug)
    };

    /**
     * 取得更新鏡像站的網址
     *
     * @return 網址
     */
    public static String getUpdateSource() {
        return String.format(Locale.getDefault(), "http://%s/geomancer/0.1.0", MIRROR_SITES[MIRROR_NUM]);
    }

    /**
     * 取得 DB 路徑
     *
     * @param context Activity 或 Service
     * @return DB 路徑
     * @throws IOException ...
     */
    public static File getDbPath(Context context) throws IOException {
        File[] dirs = context.getExternalFilesDirs("db");
        for (int i=dirs.length-1;i>=0;i--) {
            if (dirs[i]!=null) return dirs[i];
        }
        throw new IOException("");
    }

    /**
     * 取得紀錄檔路徑
     *
     * @param context Activity 或 Service
     * @return 紀錄檔路徑
     * @throws IOException ...
     */
    public static File getLogPath(Context context) throws IOException {
        File[] dirs = context.getExternalFilesDirs("log");
        for (int i=dirs.length-1;i>=0;i--) {
            if (dirs[i]!=null) return dirs[i];
        }
        throw new IOException("");
    }

    /**
     * 取得地圖路徑
     *
     * @param context Activity 或 Service
     * @return 地圖路徑
     * @throws IOException
     */
    public static File getMapPath(Context context) throws IOException {
        File[] dirs = context.getExternalFilesDirs("map");
        for (int i=dirs.length-1;i>=0;i--) {
            if (dirs[i]!=null) return dirs[i];
        }
        throw new IOException("");
    }

    /**
     * 開啟地圖
     *
     * @param context Activity 或 Service
     * @return 圖資存取介面
     * @throws IOException
     */
    public static MapDataStore openMapData(Context context) throws IOException {
        File path = new File(getMapPath(context), MAP_NAME);
        return new MapFile(path);
    }

    /**
     * 唯讀模式開啟 SQLite 資料庫
     *
     * @param context Activity 或 Service
     * @param filename 資料庫檔名
     * @return 資料庫連線
     * @throws IOException
     */
    public static SQLiteDatabase openReadOnlyDB(Context context, String filename) throws IOException {
        String path = getDbPath(context).getAbsolutePath() + "/" + filename;
        return SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY);
    }

    /**
     * 檢查是否可以傳輸資料
     *
     * @param context Activity 或 Service
     * @return 是否可以傳輸
     */
    public static boolean isNetworkConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();

        if (ni != null) {
            // TODO: 檢查是否限用 WiFi
            return ni.isConnected();
        } else {
            return false;
        }
    }

    /**
     * 清理儲存空間
     */
    public static void cleanStorage(Context context) {
        File[] dirs = context.getExternalFilesDirs("database");
        for (int i=dirs.length-1;i>=0;i--) {
            if (dirs[i]!=null) {
                try {
                    FileUtils.deleteDirectory(dirs[i]);
                } catch(IOException ex) {
                    Log.e(TAG, getReason(ex));
                }
            }
        }

        // TODO: 移除故障的殘留檔案
    }

    /**
     * 例外訊息改進程式，避免捕捉例外時還發生例外
     */
    public static String getReason(final Exception ex) {
        String msg = ex.getMessage();

        if (msg==null) {
            StackTraceElement ste = ex.getStackTrace()[0];
            msg = String.format(
                Locale.getDefault(),
                "%s with null message (%s.%s() Line:%d)",
                ex.getClass().getSimpleName(),
                ste.getClassName(),
                ste.getMethodName(),
                ste.getLineNumber()
            );
        }

        return msg;
    }

    /**
     * 是否允許透過行動網路更新
     */
    public static boolean canUpdateByMobile(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(PREFKEY_UPDATE_BY_MOBILE, true);
    }

    /**
     * 產生 Fragment 切換事件
     *
     * @param action 動作名稱
     * @return Fragment 切換事件
     */
    public static Intent buildFragmentSwitchIntent(String action) {
        Intent i = new Intent();
        i.addCategory(INTENT_CATEGORY);
        i.setAction(action);
        return i;
    }

    /**
     * 產生 Fragment 切換事件過濾器
     *
     * @return Fragment 切換事件過濾器
     */
    public static IntentFilter buildFragmentSwitchIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addCategory(INTENT_CATEGORY);
        filter.addAction("MAIN");
        filter.addAction("UPDATE");
        filter.addAction("SETTINGS");
        filter.addAction("CONTRIBUTORS");
        filter.addAction("LICENSE");
        return filter;
    }

}
