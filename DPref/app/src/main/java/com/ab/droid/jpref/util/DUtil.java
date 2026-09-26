/*  This file is part of DPref project.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see [http://www.gnu.org/licenses/].
 *
 * Copyright (C) 2026 Alexander Bootman <ab.jpref@gmail.com>
 *
 * Created: 2/3/26
 */

package com.ab.droid.jpref.util;

import com.ab.droid.jpref.MainActivity;
import com.ab.util.Util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class DUtil extends Util {

    public DUtil(MainActivity context) {
        this.host = context;
        unserialize(context.getDataDirectory());
    }

    public void serialize() {
        serialize(host.getDataDirectory());
    }

    // Util.submitLog() checks this before attempting the upload, so it fails
    // immediately with a clear message instead of deep inside the network
    // stack with a raw UnknownHostException. Rule out "no network interface
    // at all" (e.g. airplane mode) with the cheap ConnectivityManager check
    // first, instantly and without waiting on a timeout - then fall back to
    // Util's real probe of the log-upload server itself, since an interface
    // being up doesn't guarantee that specific host is actually reachable
    // (captive portal, DNS failure, server down, ...).
    @Override
    public boolean isConnected() {
        return hasActiveNetwork() && super.isConnected();
    }

    private boolean hasActiveNetwork() {
        ConnectivityManager cm = (ConnectivityManager)
            ((MainActivity) host).getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        Network network = cm.getActiveNetwork();
        NetworkCapabilities capabilities = network != null ? cm.getNetworkCapabilities(network) : null;
        return capabilities != null && (
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    public Bitmap loadBitmap(String assetPath) {
        try (InputStream is = DUtil.class.getResourceAsStream(assetPath)) {
            if (is == null) {
                return null;
            }
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            return bitmap;
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
}
