package com.android.app.search;

import static com.android.app.search.LayoutType.EMPTY_DIVIDER;
import static com.android.app.search.LayoutType.SECTION_HEADER;
import static com.android.app.search.ResultType.NO_FULFILLMENT;

import android.app.search.SearchTarget;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;

public class SearchTargetGenerator {
    private static final UserHandle USERHANDLE = Process.myUserHandle();

    public static SearchTarget EMPTY_DIVIDER_TARGET =
            new SearchTarget.Builder(NO_FULFILLMENT, EMPTY_DIVIDER, "divider")
                    .setPackageName("") /* required but not used*/
                    .setUserHandle(USERHANDLE) /* required */
                    .setExtras(new Bundle())
                    .build();

    public static SearchTarget SECTION_HEADER_TARGET =
            new SearchTarget.Builder(NO_FULFILLMENT, SECTION_HEADER, "section_header")
                    .setPackageName("") /* required but not used*/
                    .setUserHandle(USERHANDLE) /* required */
                    .setExtras(new Bundle())
                    .build();
}
