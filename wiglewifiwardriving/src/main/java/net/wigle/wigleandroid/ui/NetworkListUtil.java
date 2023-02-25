package net.wigle.wigleandroid.ui;

import android.bluetooth.BluetoothClass;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.provider.Settings;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.model.Network;
import net.wigle.wigleandroid.model.NetworkType;
import net.wigle.wigleandroid.util.Logging;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static net.wigle.wigleandroid.R.*;

/**
 * Common utility methods for the network list
 */
public class NetworkListUtil {

    //color by signal strength
    private static final int COLOR_1 = Color.rgb(0, 255, 0);
    private static final int COLOR_2 = Color.rgb(85, 255, 0);
    private static final int COLOR_3 = Color.rgb(170, 255, 0);
    private static final int COLOR_4 = Color.rgb(255, 255, 0);
    private static final int COLOR_5 = Color.rgb(255, 170, 0);
    private static final int COLOR_6 = Color.rgb(255, 85, 0);
    private static final int COLOR_7 = Color.rgb(255, 0, 0);

    private static final int COLOR_1A = Color.argb(128, 0, 255, 0);
    private static final int COLOR_2A = Color.argb(128, 85, 255, 0);
    private static final int COLOR_3A = Color.argb(128, 170, 255, 0);
    private static final int COLOR_4A = Color.argb(128, 255, 255, 0);
    private static final int COLOR_5A = Color.argb(128, 255, 170, 0);
    private static final int COLOR_6A = Color.argb(128, 255, 85, 0);
    private static final int COLOR_7A = Color.argb(128, 255, 0, 0);

    public static String getConstructionTime(final SimpleDateFormat format, final Network network) {
        return format.format(new Date(network.getConstructionTime()));
    }

    public static SimpleDateFormat getConstructionTimeFormater(final Context context) {
        final int value = Settings.System.getInt(context.getContentResolver(), Settings.System.TIME_12_24, -1);
        SimpleDateFormat format;
        if (value == 24) {
            format = new SimpleDateFormat("H:mm:ss", Locale.getDefault());
        } else {
            format = new SimpleDateFormat("h:mm:ss a", Locale.getDefault());
        }
        return format;
    }

    public static int getSignalColor(final int level, final boolean alpha) {
        int color = alpha ? COLOR_1A : COLOR_1;
        if (level <= -100) {
            color = alpha ? COLOR_7A : COLOR_7;
        } else if (level <= -90) {
            color = alpha ? COLOR_6A : COLOR_6;
        } else if (level <= -80) {
            color = alpha ? COLOR_5A : COLOR_5;
        } else if (level <= -70) {
            color = alpha ? COLOR_4A : COLOR_4;
        } else if (level <= -60) {
            color = alpha ? COLOR_3A : COLOR_3;
        } else if (level <= -50) {
            color = alpha ? COLOR_2A : COLOR_2;
        }
        return color;
    }

    @ColorInt
    public static int getTextColorForSignal(Context context, final int level) {
        Resources.Theme theme = context.getTheme();

        @ColorInt int color = context.getResources().getColor(R.color.signal_one, theme);
        if (level <= -100) {
            color = context.getResources().getColor(R.color.signal_seven, theme);
        } else if (level <= -90) {
            color = context.getResources().getColor(R.color.signal_six, theme);
        } else if (level <= -80) {
            color = context.getResources().getColor(R.color.signal_five, theme);
        } else if (level <= -70) {
            color = context.getResources().getColor(R.color.signal_four, theme);
        } else if (level <= -60) {
            color = context.getResources().getColor(R.color.signal_three, theme);
        } else if (level <= -50) {
            color = context.getResources().getColor(R.color.signal_two, theme);
        }
        return color;
    }

    public static BitmapDescriptor getSignalBitmap(@NonNull Context context, final int level) {
        int color = getSignalColor(level, true);
        return getBitmapFromVector(context, drawable.observation, color);
    }

    public static BitmapDescriptor getBitmapFromVector(@NonNull Context context,
                                                       @DrawableRes int vectorResourceId,
                                                       @ColorInt int tintColor) {

        Drawable vectorDrawable;
        vectorDrawable = ResourcesCompat.getDrawable(
                context.getResources(), vectorResourceId, null);
        if (vectorDrawable == null) {
            Logging.error("Requested vector resource was not found");
            return BitmapDescriptorFactory.defaultMarker();
        }
        Bitmap bitmap = Bitmap.createBitmap(vectorDrawable.getIntrinsicWidth(),
                vectorDrawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        vectorDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        DrawableCompat.setTint(vectorDrawable, tintColor);
        vectorDrawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    public static int getImage(final Network network) {
        int resource;
        if (null == network) {
            return drawable.no_ico;
        }
        if (network.getType().equals(NetworkType.WIFI)) {
            switch (network.getCrypto()) {
                case Network.CRYPTO_WEP:
                    resource = drawable.wep_ico;
                    break;
                case Network.CRYPTO_WPA3:
                    resource = drawable.wpa3_ico;
                    break;
                case Network.CRYPTO_WPA2:
                    resource = drawable.wpa2_ico;
                    break;
                case Network.CRYPTO_WPA:
                    resource = drawable.wpa_ico;
                    break;
                case Network.CRYPTO_NONE:
                    resource = drawable.no_ico;
                    break;
                default:
                    throw new IllegalArgumentException("unhanded crypto: " + network.getCrypto()
                            + " in network: " + network);
            }
        } else if (NetworkType.BT.equals(network.getType())) {
            resource = drawable.ic_bt;
        } else if (NetworkType.BLE.equals(network.getType())) {
            resource = drawable.ic_btle;
        } else if (NetworkType.NR.equals(network.getType())) {
            resource = drawable.cell_5g;
        } else {
            resource = drawable.ic_cell;
        }

        return resource;
    }

    public static Integer getBtImage(final Network network) {
        Integer resource;
        switch (network.getFrequency()) {
            case BluetoothClass.Device.AUDIO_VIDEO_CAMCORDER:
                resource = drawable.av_camcorder_pro_f;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO:
                resource = drawable.av_car_f_smile;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE:
                resource = drawable.av_handsfree_headset_f;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES:
                resource = drawable.av_headphone_f;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO:
                resource = drawable.av_hifi_f;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER:
                resource = drawable.av_speaker_f_detailed;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_MICROPHONE:
                resource = drawable.av_mic_f;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO:
                resource = drawable.av_boombox_f;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX:
                resource = drawable.av_settop_f;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_UNCATEGORIZED:
                resource = drawable.av_receiver_f;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_VCR:
                resource = drawable.av_vcr_f;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_CAMERA:
                resource = drawable.av_camcorder_f;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_CONFERENCING:
                resource = drawable.av_conference;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER:
                resource = drawable.av_receiver_f;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_GAMING_TOY:
                resource = drawable.av_toy;
                break;
            case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR:
                resource = drawable.av_monitor;
                break;
            case BluetoothClass.Device.COMPUTER_DESKTOP:
                resource = drawable.comp_desk_f;
                break;
            case BluetoothClass.Device.COMPUTER_HANDHELD_PC_PDA:
                resource = drawable.comp_handheld;
                break;
            case BluetoothClass.Device.COMPUTER_LAPTOP:
                resource = drawable.comp_laptop;
                break;
            case BluetoothClass.Device.COMPUTER_PALM_SIZE_PC_PDA:
                resource = drawable.comp_laptop_sm;
                break;
            case BluetoothClass.Device.COMPUTER_SERVER:
                resource = drawable.comp_server_f;
                break;
            case BluetoothClass.Device.COMPUTER_UNCATEGORIZED:
                resource = drawable.comp_server_desk_f;
                break;
            case BluetoothClass.Device.COMPUTER_WEARABLE:
                resource = drawable.comp_ar_f;
                break;
            case BluetoothClass.Device.HEALTH_BLOOD_PRESSURE:
                resource = drawable.med_heart;
                break;
            case BluetoothClass.Device.HEALTH_DATA_DISPLAY:
                resource = drawable.med_heart_display_o;
                break;
            case BluetoothClass.Device.HEALTH_PULSE_OXIMETER:
            case BluetoothClass.Device.HEALTH_PULSE_RATE:
                resource = drawable.med_heart;
                break;
            case BluetoothClass.Device.HEALTH_GLUCOSE:
            case BluetoothClass.Device.HEALTH_THERMOMETER:
            case BluetoothClass.Device.HEALTH_UNCATEGORIZED:
                resource = drawable.med_cross_f;
                break;
            case BluetoothClass.Device.HEALTH_WEIGHING:
                resource = drawable.med_scale_f;
                break;
            case BluetoothClass.Device.PHONE_CELLULAR:
                resource = drawable.tel_cell;
                break;
            case BluetoothClass.Device.PHONE_CORDLESS:
                resource = drawable.tel_cordless_1;
                break;
            case BluetoothClass.Device.PHONE_ISDN:
                resource = drawable.tel_isdn;
                break;
            case BluetoothClass.Device.PHONE_MODEM_OR_GATEWAY:
                resource = drawable.tel_modem;
                break;
            case BluetoothClass.Device.PHONE_SMART:
                resource = drawable.comp_handheld;
                break;
            case BluetoothClass.Device.PHONE_UNCATEGORIZED:
                resource = drawable.tel_phone_2;
                break;
            case BluetoothClass.Device.TOY_CONTROLLER:
                resource = drawable.toy_controller_f;
                break;
            case BluetoothClass.Device.TOY_DOLL_ACTION_FIGURE:
                resource = drawable.av_toy;
                break;
            case BluetoothClass.Device.TOY_GAME:
                resource = drawable.av_toy;
                break;
            case BluetoothClass.Device.TOY_ROBOT:
                resource = drawable.toy_robot;
                break;
            case BluetoothClass.Device.TOY_UNCATEGORIZED:
                resource = drawable.av_toy;
                break;
            case BluetoothClass.Device.TOY_VEHICLE:
                resource = drawable.toy_vehicle;
                break;
            case BluetoothClass.Device.WEARABLE_GLASSES:
                resource = drawable.wear_glasses_1;
                break;
            case BluetoothClass.Device.WEARABLE_HELMET:
                resource = drawable.wear_helmet;
                break;
            case BluetoothClass.Device.WEARABLE_JACKET:
                resource = drawable.wear_jacket;
                break;
            case BluetoothClass.Device.WEARABLE_PAGER:
                resource = drawable.wear_pager;
                break;
            case BluetoothClass.Device.WEARABLE_UNCATEGORIZED:
                resource = drawable.wear_jacket_2;
                break;
            case BluetoothClass.Device.WEARABLE_WRIST_WATCH:
                resource = drawable.wear_watch;
                break;
            default:
                resource = null;
        }
        return resource;
    }
}
