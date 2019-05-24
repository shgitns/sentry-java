package io.sentry.android;

import java.util.concurrent.Callable;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SentryAndroidIT extends AndroidTest {

    @Test
    public void testInitializingUsingBaseContext() throws Exception {
        verifyProject1PostRequestCount(0);
        verifyStoredEventCount(0);

        SentryITActivityUsingBaseContext activity = Robolectric.setupActivity(SentryITActivityUsingBaseContext.class);
        Assert.assertEquals(activity.getCustomFactoryUsed(), true);

        activity.sendEvent();
        waitUntilTrue(1000, new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return getStoredEvents().size() == 1;
            }
        });

        verifyProject1PostRequestCount(1);
        verifyStoredEventCount(1);
    }

    @Test
    public void testInitializingUsingApplication() throws Exception {
        verifyProject1PostRequestCount(0);
        verifyStoredEventCount(0);

        SentryITActivityUsingApplication activity = Robolectric.setupActivity(SentryITActivityUsingApplication.class);
        Assert.assertEquals(activity.getCustomFactoryUsed(), true);

        activity.sendEvent();
        waitUntilTrue(1000, new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return getStoredEvents().size() == 1;
            }
        });

        verifyProject1PostRequestCount(1);
        verifyStoredEventCount(1);
    }
}
