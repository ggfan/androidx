/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.support.v4.media;

import android.content.Intent;
import android.media.MediaDescription;
import android.media.browse.MediaBrowser;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.service.media.MediaBrowserService;

import java.util.ArrayList;
import java.util.List;

class MediaBrowserServiceCompatApi21 {
    private static Object sNullParceledListSliceObj;
    static {
        MediaDescription nullDescription = new MediaDescription.Builder().setMediaId(
                MediaBrowserCompatApi21.NULL_MEDIA_ITEM_ID).build();
        MediaBrowser.MediaItem nullMediaItem = new MediaBrowser.MediaItem(nullDescription, 0);
        List<MediaBrowser.MediaItem> nullMediaItemList = new ArrayList<>();
        nullMediaItemList.add(nullMediaItem);
        sNullParceledListSliceObj = ParceledListSliceAdapterApi21.newInstance(nullMediaItemList);
    }

    public static Object createService() {
        return new MediaBrowserServiceAdaptorApi21();
    }

    public static void onCreate(Object serviceObj, ServiceImplApi21 serviceImpl) {
        ((MediaBrowserServiceAdaptorApi21) serviceObj).onCreate(serviceImpl);
    }

    public static IBinder onBind(Object serviceObj, Intent intent) {
        return ((MediaBrowserServiceAdaptorApi21) serviceObj).onBind(intent);
    }

    public static Object parcelListToParceledListSliceObject(List<Parcel> list) {
        if (list == null) {
            if (Build.VERSION.SDK_INT <= 23) {
                return sNullParceledListSliceObj;
            }
            return null;
        }
        List<MediaBrowser.MediaItem> itemList = new ArrayList<>();
        for (Parcel parcel : list) {
            parcel.setDataPosition(0);
            itemList.add(MediaBrowser.MediaItem.CREATOR.createFromParcel(parcel));
            parcel.recycle();
        }
        return ParceledListSliceAdapterApi21.newInstance(itemList);
    }

    public interface ServiceImplApi21 {
        void connect(String pkg, Bundle rootHints, ServiceCallbacksApi21 callbacks);
        void disconnect(ServiceCallbacksApi21 callbacks);
        void addSubscription(String id, ServiceCallbacksApi21 callbacks);
        void removeSubscription(String id, ServiceCallbacksApi21 callbacks);
    }

    public interface ServiceCallbacksApi21 {
        IBinder asBinder();
        void onConnect(String root, Object session, Bundle extras) throws RemoteException;
        void onConnectFailed() throws RemoteException;
        void onLoadChildren(String mediaId, List<Parcel> list) throws RemoteException;
    }

    public static class ServiceCallbacksImplApi21 implements ServiceCallbacksApi21 {
        final ServiceCallbacksAdapterApi21 mCallbacks;

        ServiceCallbacksImplApi21(Object callbacksObj) {
            mCallbacks = createCallbacks(callbacksObj);
        }

        public IBinder asBinder() {
            return mCallbacks.asBinder();
        }

        public void onConnect(String root, Object session, Bundle extras) throws RemoteException {
            mCallbacks.onConnect(root, session, extras);
        }

        public void onConnectFailed() throws RemoteException {
            mCallbacks.onConnectFailed();
        }

        public void onLoadChildren(String mediaId, List<Parcel> list) throws RemoteException {
            mCallbacks.onLoadChildren(mediaId, parcelListToParceledListSliceObject(list));
        }

        ServiceCallbacksAdapterApi21 createCallbacks(Object callbacksObj) {
            return new ServiceCallbacksAdapterApi21(callbacksObj);
        }
    }

    static class MediaBrowserServiceAdaptorApi21 {
        Binder mBinder;

        public void onCreate(ServiceImplApi21 serviceImpl) {
            mBinder = createServiceBinder(serviceImpl);
        }

        public IBinder onBind(Intent intent) {
            if (MediaBrowserService.SERVICE_INTERFACE.equals(intent.getAction())) {
                return mBinder;
            }
            return null;
        }

        protected Binder createServiceBinder(ServiceImplApi21 serviceImpl) {
            return new ServiceBinderAdapterApi21(serviceImpl);
        }
    }
}
