






















































package com.sonyericsson.android.camera.controller.album;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import com.sonyericsson.android.camera.util.CamLog;
import com.sonyericsson.cameracommon.contentsview.PredictiveCaptureStoreInfo;
import com.sonyericsson.cameracommon.mediasaving.MediaSavingConstants;
import com.sonyericsson.cameracommon.utility.CommonUtility;
import com.sonymobile.cameracommon.research.ResearchUtil;
import java.util.List;

public final class AlbumLauncher {
    public static final String EXTRA_BURST_BUCKETID = "burst_bucketId";
    public static final String TAG = "AlbumLauncher";

    private enum MimeType {
        PHOTO(MediaSavingConstants.MEDIA_TYPE_JPEG_MIME),
        MPO(MediaSavingConstants.MEDIA_TYPE_MPO_MIME),
        MP4(MediaSavingConstants.MEDIA_TYPE_MPEG4_MIME),
        THREEGPP(MediaSavingConstants.MEDIA_TYPE_3GP_MIME),
        UNKOWN("");

        final String mText;

        MimeType(String str) {
            this.mText = str;
        }

        static MimeType fromText(String str) {
            for (MimeType mimeType : values()) {
                if (mimeType.mText.equals(str)) {
                    return mimeType;
                }
            }
            return UNKOWN;
        }
    }

    public static void launchAlbum(Activity activity, Uri uri, String str, int i, boolean z) {
        launchAlbum(activity, uri, str, i, z, true);
    }

    public static void launchAlbum(Activity activity, Uri uri, String str, int i, boolean z, boolean z2) {
        launchAlbum(activity, uri, str, i, z, z2, null);
    }

    public static void launchAlbum(Activity activity, Uri uri, String str, int i, boolean z, boolean z2, PredictiveCaptureStoreInfo predictiveCaptureStoreInfo) {
        if (CamLog.DEBUG) {
            CamLog.d("launchAlbum(" + uri + ", " + str + ", " + i + ", " + z + ", " + z2 + ", " + predictiveCaptureStoreInfo + ")");
        }
        Intent intent = new Intent("com.android.camera.action.REVIEW");
        intent.addCategory("android.intent.category.DEFAULT");
        if (MimeType.fromText(str) == MimeType.MPO) {
            intent.setDataAndType(uri, MimeType.PHOTO.mText);
        } else {
            intent.setDataAndType(uri, str);
        }
        CommonUtility.DefaultGallerySetting defaultGallery = CommonUtility.getDefaultGallery(activity.getApplicationContext(), uri, str);
        if (CamLog.DEBUG) {
            CamLog.d("launchAlbum defaultGallery " + defaultGallery);
        }
        switch (defaultGallery) {
            case OTHER:
            case SONY_ALBUM:
                if (z) {
                    intent.putExtra(EXTRA_BURST_BUCKETID, i);
                }
                if (z2) {
                    intent.putExtra(com.sonyericsson.album.fastview.Intent.EXTRA_FAST_VIEW_MODE, true);
                }
                if (predictiveCaptureStoreInfo != null) {
                    intent.putExtra("com.sonymobile.album.intent.extra.PREDICTIVE_CAPTURE_COUNT", predictiveCaptureStoreInfo.getCaptureNum());
                    break;
                }
                break;
        }
        if (CommonUtility.isActivityAvailable(activity.getApplicationContext(), intent)) {
            activity.startActivityForResult(intent, 8);
        } else {
            launchReviewApp(activity, uri, str, 8, false);
        }
        ResearchUtil.getInstance().setViewerLaunched();
    }

    public static void launchAlbumSecure(Activity activity, List<Uri> list, List<String> list2, PredictiveCaptureStoreInfo predictiveCaptureStoreInfo, long[] jArr) {
        if (list.size() == 0 || list2.size() == 0) {
            throw new RuntimeException("album image uri is empty.");
        }
        if (CamLog.DEBUG) {
            CamLog.d("launchAlbumSecure(" + list + ", " + list2 + ", " + jArr + ", " + predictiveCaptureStoreInfo + ")");
        }
        Intent intent = new Intent("com.android.camera.action.REVIEW");
        intent.setDataAndType(list.get(0), list2.get(0));
        intent.addCategory("android.intent.category.DEFAULT");
        switch (CommonUtility.getDefaultGallery(activity.getApplicationContext(), list.get(0), list2.get(0))) {
            case OTHER:
                intent.putExtra(com.sonyericsson.album.fastview.Intent.EXTRA_FAST_VIEW_MODE, true);
                if (predictiveCaptureStoreInfo != null) {
                    intent.putExtra("com.sonymobile.album.intent.extra.PREDICTIVE_CAPTURE_COUNT", predictiveCaptureStoreInfo.getCaptureNum());
                    break;
                }
                break;
            case SONY_ALBUM:
                intent.putExtra(com.sonyericsson.album.fastview.Intent.EXTRA_FAST_VIEW_MODE, true);
                if (predictiveCaptureStoreInfo != null) {
                    intent.putExtra("com.sonymobile.album.intent.extra.PREDICTIVE_CAPTURE_COUNT", predictiveCaptureStoreInfo.getCaptureNum());
                }
                intent.putExtra("com.google.android.apps.photos.api.secure_mode_ids", jArr);
                intent.putExtra("com.google.android.apps.photos.api.secure_mode", true);
                break;
            case GOOGLE_PHOTOS:
                intent.putExtra("com.google.android.apps.photos.api.secure_mode_ids", jArr);
                intent.putExtra("com.google.android.apps.photos.api.secure_mode", true);
                break;
        }
        if (CommonUtility.isActivityAvailable(activity.getApplicationContext(), intent)) {
            activity.startActivityForResult(intent, 9);
        } else {
            launchReviewApp(activity, list.get(0), list2.get(0), 9, true);
        }
        ResearchUtil.getInstance().setViewerLaunched();
    }

    private static void launchReviewApp(Activity activity, Uri uri, String str, int i, boolean z) {
        // Android 10（API 29）之後 AOSP / LineageOS 圖庫只註冊 MediaStore.ACTION_REVIEW，
        // 不再接受舊的 "com.android.camera.action.REVIEW"。若直接退回 ACTION_VIEW，圖庫會
        // 把內容當成外部唯讀項目，只剩「分享」而沒有刪除／編輯／我的最愛，所以先試
        // ACTION_REVIEW(_SECURE)，真的沒人接才退回 ACTION_VIEW。
        Intent intent = new Intent(z ? MediaStore.ACTION_REVIEW_SECURE : MediaStore.ACTION_REVIEW);
        intent.setDataAndType(uri, str);
        try {
            activity.startActivityForResult(intent, i);
            return;
        } catch (ActivityNotFoundException unused) {
            if (CamLog.DEBUG) {
                CamLog.d("There is no activity which accepts action:" + intent.getAction());
            }
        }
        Intent intent2 = new Intent("android.intent.action.VIEW");
        intent2.setDataAndType(uri, str);
        try {
            activity.startActivityForResult(intent2, i);
        } catch (ActivityNotFoundException unused) {
            if (CamLog.DEBUG) {
                CamLog.d("There is no activity which accepts action:Intent.ACTION_VIEW");
            }
        }
    }
}
