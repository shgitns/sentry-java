package io.sentry.android;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import io.sentry.DefaultSentryClientFactory;
import io.sentry.SentryClient;
import io.sentry.android.event.helper.AndroidEventBuilderHelper;
import io.sentry.buffer.Buffer;
import io.sentry.buffer.DiskBuffer;
import io.sentry.config.Lookup;
import io.sentry.context.ContextManager;
import io.sentry.context.SingletonContextManager;
import io.sentry.dsn.Dsn;
import io.sentry.util.Util;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;

/**
 * SentryClientFactory that handles Android-specific construction, like taking advantage
 * of the Android Context instance.
 */
public class AndroidSentryClientFactory extends DefaultSentryClientFactory {

    /**
     * Logger tag.
     */
    public static final String TAG = AndroidSentryClientFactory.class.getName();

    /**
     * Default Buffer directory name.
     */
    private static final String DEFAULT_BUFFER_DIR = "sentry-buffered-events";

    private WeakReference<Context> ctx;

    /**
     * Construct an AndroidSentryClientFactory using the base Context from the specified Android Application.
     *
     * @param app Android Application
     */
    public AndroidSentryClientFactory(Application app) {
        Log.d(TAG, "Construction of Android Sentry from Android Application.");

        this.ctx = new WeakReference<>(app.getBaseContext());
    }

    /**
     * Construct an AndroidSentryClientFactory using the specified Android Context.
     *
     * @param ctx Android Context.
     */
    public AndroidSentryClientFactory(Context ctx) {
        Log.d(TAG, "Construction of Android Sentry from Android Context.");

        this.ctx = new WeakReference<>(ctx);
    }

    private Context getAndroidContext() {
        Context context = ctx.get();
        if (context == null) {
            throw new IllegalStateException("Application context no longer available!" +
                "Ensure that you supply the root context of your application via Application.getBaseContext()" +
                "or the Application class itself to this class constructor.");
        }

        return context;
    }

    @Override
    public SentryClient createSentryClient(Dsn dsn) {
        if (!checkPermission(Manifest.permission.INTERNET)) {
            Log.e(TAG, Manifest.permission.INTERNET + " is required to connect to the Sentry server,"
                + " please add it to your AndroidManifest.xml");
        }

        Context context = getAndroidContext();
        Log.d(TAG, "Sentry init with ctx='" + context.toString() + "'");

        String protocol = dsn.getProtocol();
        if (protocol.equalsIgnoreCase("noop")) {
            Log.w(TAG, "*** Couldn't find a suitable DSN, Sentry operations will do nothing!"
                + " See documentation: https://docs.sentry.io/clients/java/modules/android/ ***");
        } else if (!(protocol.equalsIgnoreCase("http") || protocol.equalsIgnoreCase("https"))) {
            String async = Lookup.lookup(DefaultSentryClientFactory.ASYNC_OPTION, dsn);
            if (async != null && async.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException("Sentry Android cannot use synchronous connections, remove '"
                    + DefaultSentryClientFactory.ASYNC_OPTION + "=false' from your options.");
            }

            throw new IllegalArgumentException("Only 'http' or 'https' connections are supported in"
                + " Sentry Android, but received: " + protocol);
        }

        SentryClient sentryClient = super.createSentryClient(dsn);
        sentryClient.addBuilderHelper(new AndroidEventBuilderHelper(context));

        return sentryClient;
    }

    @Override
    protected Collection<String> getInAppFrames(Dsn dsn) {
        Collection<String> inAppFrames = super.getInAppFrames(dsn);

        if (inAppFrames.isEmpty()) {
            return getInAppFramesFromAndroidContext();
        }

        return inAppFrames;
    }

    @Override
    protected Buffer getBuffer(Dsn dsn) {
        File bufferDir;
        String bufferDirOpt = Lookup.lookup(BUFFER_DIR_OPTION, dsn);
        if (bufferDirOpt != null) {
            bufferDir = new File(bufferDirOpt);
        } else {
            bufferDir = new File(getAndroidContext().getCacheDir().getAbsolutePath(), DEFAULT_BUFFER_DIR);
        }

        Log.d(TAG, "Using buffer dir: " + bufferDir.getAbsolutePath());
        return new DiskBuffer(bufferDir, getBufferSize(dsn));
    }

    @Override
    protected ContextManager getContextManager(Dsn dsn) {
        return new SingletonContextManager();
    }

    private Collection<String> getInAppFramesFromAndroidContext() {
        Context context = getAndroidContext();
        String packageName = context.getPackageName();
        PackageManager packageManager = context.getPackageManager();

        try {
            PackageInfo info = packageManager.getPackageInfo(packageName, 0);

            return info == null || Util.isNullOrEmpty(info.packageName)
                ? Collections.<String>emptyList()
                : Collections.singletonList(info.packageName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error getting package information.", e);
            return Collections.emptyList();
        }
    }

    /**
     * Check whether the application has been granted a certain permission.
     *
     * @param permission Permission as a string
     *
     * @return true if permissions is granted
     */
    private boolean checkPermission(String permission) {
        int res = getAndroidContext().checkCallingOrSelfPermission(permission);
        return (res == PackageManager.PERMISSION_GRANTED);
    }
}
