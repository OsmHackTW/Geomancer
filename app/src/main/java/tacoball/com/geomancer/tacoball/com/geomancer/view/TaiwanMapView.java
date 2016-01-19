package tacoball.com.geomancer.tacoball.com.geomancer.view;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;

import org.apache.commons.io.IOUtils;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.rendertheme.AssetsRenderTheme;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.overlay.Marker;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;

/**
 *
 */
public class TaiwanMapView extends MapView {

    private static final String TAG = "TacoMapView";
    private static final String MAP_NAME = "taiwan-taco.map";

    private Context         mContext;
    private MapDataStore    mMapDataStore;
    private SensorManager   mSensorMgr;
    private LocationManager mLocationMgr;
    private int             mMyLocationImage;
    private Marker          mMyLocationMarker;

    private State mState = new State();
    private StateChangeListener mStateChangeListener;

    private boolean mOneTimePositioning = false;
    private boolean mGpsEnabled         = false;

    private long mPrevOnDraw = 10;

    public static class State {
        public double cLat;
        public double cLng;
        public int    zoom;
        public double myLat = -1;
        public double myLng = -1;
        public double myAzimuth;
    }

    public interface StateChangeListener {
        void onStateChanged(State state);
    }

    public TaiwanMapView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);

        try {
            mContext     = context;
            mSensorMgr   = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
            mLocationMgr = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);

            Sensor rv = mSensorMgr.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            mSensorMgr.registerListener(mAzimuthListener, rv, SensorManager.SENSOR_DELAY_UI);
            initView();
        } catch(IOException ex) {
            Log.e(TAG, ex.getMessage());
        }
    }

    public void setStateChangeListener(StateChangeListener listener) {
        mStateChangeListener = listener;
    }

    public void setMyLocationImage(int resId) {
        // API 21
        //Drawable d = mContext.getDrawable(resId);
        Drawable d = mContext.getResources().getDrawable(resId);

        if (d!=null) {
            mMyLocationImage = resId;
            if (mMyLocationMarker!=null) {
                getLayerManager().getLayers().remove(mMyLocationMarker);
            }

            org.mapsforge.core.graphics.Bitmap markerBitmap = AndroidGraphicFactory.convertToBitmap(d);
            mMyLocationMarker = new Marker(new LatLong(25.0f, 121.0f), markerBitmap, 0, 0);
            getLayerManager().getLayers().add(mMyLocationMarker);
        }
    }

    public void gotoMyPosition() {
        mOneTimePositioning = true;
        if (!mGpsEnabled) {
            enableGps();
        }
    }

    /*
    public void startTracing() {
        mOneTimePositioning = false;
        if (!mGpsEnabled) {
            enableGps();
        }
    }

    public void cancelTracing() {
        if (mGpsEnabled&&!mOneTimePositioning) {
            disableGps();
        }
    }
    */

    @Override
    public void destroy() {
        // TODO: Remove this after #659 solved
        // Avoid Issue #659, https://github.com/mapsforge/mapsforge/issues/659
        mMyLocationMarker.setBitmap(null);

        // Save state
        mContext.getSharedPreferences(TAG, Context.MODE_PRIVATE)
            .edit()
            .putFloat("cLat", (float)mState.cLat)
            .putFloat("cLng", (float)mState.cLng)
            .putInt("zoom", mState.zoom)
            .commit();

        mSensorMgr.unregisterListener(mAzimuthListener);
        disableGps();
        super.destroy();
    }

    public static File getAppliedMapFile(Context context) {
        File[] dirs = context.getExternalFilesDirs("map");
        File f = null;

        if (dirs!=null && dirs.length>0) {
            f = new File(dirs[dirs.length-1], MAP_NAME);
        }

        return f;
    }

    public static String getCurrentMapFile(Context context) {
        try {
            String[] assets = context.getAssets().list("");
            for (String s : assets) {
                if (s.matches("^gzipped-taiwan-taco-\\d+\\.map$")) {
                    return s;
                }
            }
        } catch(IOException e) {
            Log.e(TAG, "Cannot list asset");
        }

        Log.e(TAG, "Mapfile not found");
        return null;
    }

    public static boolean hasNewMapFile(Context context) {
        File f = getAppliedMapFile(context);
        if (!f.exists()) return true;

        SharedPreferences pref = context.getSharedPreferences(TAG, Context.MODE_PRIVATE);
        int applyVer = pref.getInt("map.version", 0);

        String currMapFile = getCurrentMapFile(context);
        int    currVer     = Integer.valueOf(currMapFile.substring(20,30));

        return (currVer>applyVer);
    }

    public static void extractMapFile(Context context, MapUpdateListener listener) {
        try {
            Log.i(TAG, "解壓縮圖資");
            listener.onDecompress(0);

            String currMapFile = getCurrentMapFile(context);
            GZIPInputStream in = new GZIPInputStream(context.getAssets().open(currMapFile));

            File f = getAppliedMapFile(context);
            OutputStream out = new FileOutputStream(f);

            // gzip len = 25400590
            // map  len = 38204880
            /*
            long size = 38204880; // TODO: get ungzipped size from API
            long one_percent = (long)Math.ceil(size/100.0);
            for (int p=0;p<100;p++) {
                IOUtils.copyLarge(in, out, 0, one_percent);
                listener.onDecompress(p + 1);
                //Log.e(TAG, String.format("ungzip: %d%%", p + 1));
            }
            */
            IOUtils.copyLarge(in, out);

            out.flush();
            out.close();
            in.close();

            SharedPreferences pref = context.getSharedPreferences(TAG, Context.MODE_PRIVATE);
            int currVer = Integer.valueOf(currMapFile.substring(20, 30));
            pref.edit()
                .putInt("map.version", currVer)
                .apply();

            Log.i(TAG, "解壓縮完成");
        } catch(IOException ex) {
            String msg = String.format(
                "extractMapFile Failed: %s %s",
                ex.getClass().getSimpleName(),
                ex.getMessage()
            );
            Log.e(TAG, msg);
        }
    }

    private void enableGps() {
        // 接收方位感應器和 GPS 訊號
        mGpsEnabled = true;
        // java.lang.IllegalArgumentException: provider doesn't exist: network
        mLocationMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 10.0f, mLocListener);
        //mLocationMgr.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 10.0f, mLocListener);
    }

    private void disableGps() {
        mLocationMgr.removeUpdates(mLocListener);
        mGpsEnabled = false;
    }

    private void initView() throws IOException {
        final boolean SEE_DEBUG_POINT = true;
        final byte MIN_ZOOM = 7;
        final byte MAX_ZOOM = 17;

        AndroidGraphicFactory.clearResourceFileCache();
        AndroidGraphicFactory.clearResourceMemoryCache();

        if (SEE_DEBUG_POINT) {
            // 檢查點 121.4407269 25.0179735
            mState.cLat = 25.0565;
            mState.cLng = 121.5317;
            mState.zoom = 11;
        } else {
            // Load state or initial state
            SharedPreferences pref = mContext.getSharedPreferences(TAG, Context.MODE_PRIVATE);
            mState.cLat = pref.getFloat("cLat", 25.0019f);
            mState.cLng = pref.getFloat("cLng", 121.3524f);
            mState.zoom = pref.getInt("zoom", 16);
        }

        File mapFile  = getAppliedMapFile(mContext);
        mMapDataStore = new MapFile(mapFile);

        // add Layer to mapView
        getLayerManager().getLayers().add(loadThemeLayer("themes/TaiwanGrounds", false));
        getLayerManager().getLayers().add(loadThemeLayer("themes/TaiwanRoads"));
        getLayerManager().getLayers().add(loadThemeLayer("themes/TaiwanPoints"));

        // set UI of mapView
        setClickable(true);
        setCenter(new LatLong(mState.cLat, mState.cLng));
        setZoomLevel((byte)mState.zoom);
        getMapZoomControls().setZoomLevelMin(MIN_ZOOM);
        getMapZoomControls().setZoomLevelMax(MAX_ZOOM);
        getMapZoomControls().setAutoHide(true);
        getMapZoomControls().show();
        getModel().mapViewPosition.setMapLimit(mMapDataStore.boundingBox());
    }

    private TileRendererLayer loadThemeLayer(String themeName) throws IOException {
        return loadThemeLayer(themeName, true);
    }

    private TileRendererLayer loadThemeLayer(String themeName, boolean isTransparent) throws IOException {
        String themeFileName  = String.format("%sTheme.xml", themeName);
        String themeCacheName = String.format("%sCache", themeName);

        TileCache cache = AndroidUtil.createTileCache(
            mContext,
            themeCacheName,
            getModel().displayModel.getTileSize(),
            1f,
            getModel().frameBufferModel.getOverdrawFactor()
        );

        TileRendererLayer layer = new TileRendererLayer(
            cache,
            mMapDataStore,
            getModel().mapViewPosition,
            isTransparent,
            true,
            AndroidGraphicFactory.INSTANCE
        );

        AssetsRenderTheme theme = new AssetsRenderTheme(mContext, "", themeFileName);
        layer.setXmlRenderTheme(theme);

        return layer;
    }

    private void triggerStateChange() {
        if (mStateChangeListener!=null) {
            mStateChangeListener.onStateChanged(mState);
        }
    }

    @Override
    protected void onDraw(Canvas androidCanvas) {
        super.onDraw(androidCanvas);

        // control rate of triggerStateChange()
        long fps = 50;
        long currOnDraw = System.currentTimeMillis();
        if (currOnDraw-mPrevOnDraw>=1000/fps) {
            mState.cLat = getModel().mapViewPosition.getCenter().latitude;
            mState.cLng = getModel().mapViewPosition.getCenter().longitude;
            mState.zoom = getModel().mapViewPosition.getZoomLevel();
            mPrevOnDraw = currOnDraw;
            triggerStateChange();
        }
    }

    private SensorEventListener mAzimuthListener = new SensorEventListener() {

        private static final double AZIMUTH_THRESHOLD = 3.0;
        private static final long   MILLIS_THRESHOLD  = 5000;

        long   prevMillis  = 0;
        double prevAzimuth = 0;

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType()==Sensor.TYPE_ROTATION_VECTOR) {
                long    currMillis  = System.currentTimeMillis();
                double  currAzimuth = Math.toDegrees(Math.asin(event.values[2])*2);
                boolean overAzimuthThreshold = Math.abs(currAzimuth-prevAzimuth)>=AZIMUTH_THRESHOLD;
                boolean overMillisThreshold  = (currMillis-prevMillis)>=MILLIS_THRESHOLD;

                if (overAzimuthThreshold || overMillisThreshold) {
                    prevMillis  = currMillis;
                    prevAzimuth = currAzimuth;
                    mState.myAzimuth = currAzimuth;

                    if (mMyLocationMarker!=null) {
                        Matrix matrix = new Matrix();
                        matrix.postRotate((int)-mState.myAzimuth);

                        Bitmap src = BitmapFactory.decodeResource(mContext.getResources(), mMyLocationImage);
                        Bitmap dst = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
                        Drawable d = new BitmapDrawable(mContext.getResources(), dst);

                        org.mapsforge.core.graphics.Bitmap markerBitmap = AndroidGraphicFactory.convertToBitmap(d);
                        mMyLocationMarker.setBitmap(markerBitmap);
                        mMyLocationMarker.requestRedraw();
                    }

                    triggerStateChange();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) { /* unused */ }

    };

    private LocationListener mLocListener = new LocationListener() {

        @Override
        public void onLocationChanged(Location location) {
            LatLong newLoc = new LatLong(location.getLatitude(), location.getLongitude());
            mState.myLat = newLoc.latitude;
            mState.myLng = newLoc.longitude;
            triggerStateChange();

            String msg = String.format("我的位置: (%.2f, %.2f)", mState.myLat, mState.myLng);
            Log.i(TAG, msg);

            if (mMyLocationMarker!=null) {
                mMyLocationMarker.setLatLong(newLoc);
            }

            if (mOneTimePositioning) {
                disableGps();
            }

            getModel().mapViewPosition.setCenter(newLoc);
            getModel().mapViewPosition.setZoomLevel((byte)16);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            String msg = String.format("%s: status=%d", provider, status);
            Log.e(TAG, msg);
        }

        @Override
        public void onProviderEnabled(String provider) { /* unused */ }

        @Override
        public void onProviderDisabled(String provider) { /* unused */ }

    };

}
