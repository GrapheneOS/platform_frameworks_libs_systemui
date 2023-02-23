/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.app.search;

import static com.android.app.search.LayoutType.TALL_CARD_WITH_IMAGE_NO_ICON;

import android.app.blob.BlobHandle;
import android.app.search.SearchAction;
import android.app.search.SearchTarget;
import android.text.TextUtils;

import androidx.annotation.Nullable;

/**
 * Helper class that defines key string value for {@link SearchTarget#getExtras()}
 * and also defines helper methods
 */
public class SearchTargetExtras {

    /** on device data related extras and helper methods */
    // Used to extra component name
    public static final String BUNDLE_EXTRA_CLASS = "class";

    // Used for UI treatment. Labels whether search target should support quick launch
    public static final String BUNDLE_EXTRA_QUICK_LAUNCH = "quick_launch";
    // Used for UI treatment. Targets grouped with same group id are decorated together.
    public static final String BUNDLE_EXTRA_GROUP_ID = "group_id";
    public static final String BUNDLE_EXTRA_GROUP_DECORATE_TOGETHER = "decorate_together";
    // Used if slice title should be rendered else where outside of slice (e.g., edit text)
    public static final String BUNDLE_EXTRA_SLICE_TITLE = "slice_title";
    // Used if slice view should be rendered using full height mode.
    public static final String BUNDLE_EXTRA_USE_FULL_HEIGHT = "use_full_height";
    public static final String BUNDLE_EXTRA_IS_NON_TAPPABLE = "is_non_tappable";
    public static final String BUNDLE_EXTRA_TITLE_OVERWRITE = "title_overwrite";
    // Used if subtitle view should be overridden to string that is not natively defined by the
    // search target.
    public static final String BUNDLE_EXTRA_SUBTITLE_OVERRIDE = "subtitle_override";

    // Used for logging. Returns whether spelling correction was applied.
    public static final String BUNDLE_EXTRA_IS_QUERY_CORRECTED = "is_query_corrected";
    // Used for logging. Returns whether the result matched block title or the inline item.
    public static final String BUNDLE_EXTRA_RESULT_MATCH_USER_TYPED = "result_match_user_typed";
    // Used for logging. Returns the timestamp when system service received the data.
    public static final String BUNDLE_EXTRA_START_TIMESTAMP = "start_timestamp";
    // Indicates the search result app location column
    public static final String BUNDLE_EXTRA_RESULT_APP_GRIDX = "app_gridx";

    // Used for thumbnail loading. Contains handle to retrieve Blobstore asset.
    public static final String BUNDLE_EXTRA_BLOBSTORE_HANDLE = "blobstore_handle_key";

    public static final int GROUPING = 1 << 1;

    @Nullable
    public static String getDecoratorId(@Nullable SearchTarget target) {
        return isTargetOrExtrasNull(target) ? null :
                target.getExtras().getString(BUNDLE_EXTRA_GROUP_ID);
    }

    public static int getDecoratorType(@Nullable SearchTarget target) {
        int type = 0;
        if (isTargetOrExtrasNull(target)) {
            return type;
        }
        if (!TextUtils.isEmpty(target.getExtras().getString(BUNDLE_EXTRA_GROUP_ID))) {
            type |= GROUPING;
        }
        return type;
    }

    /** Whether or not the SearchTarget's Extras contains a blobstore image. */
    public static boolean isSearchTargetBlobstoreAsset(@Nullable SearchTarget target) {
        if (isTargetOrExtrasNull(target)) {
            return false;
        }
        return target.getExtras().getParcelable(
                BUNDLE_EXTRA_BLOBSTORE_HANDLE) instanceof BlobHandle;
    }

    private static boolean isTargetOrExtrasNull(@Nullable SearchTarget target) {
        return target == null || target.getExtras() == null;
    }

    /** Web data related extras and helper methods */
    public static final String BUNDLE_EXTRA_PROXY_WEB_ITEM = "proxy_web_item";
    public static final String BUNDLE_EXTRA_ENTITY = "is_entity";
    public static final String BUNDLE_EXTRA_ANSWER = "is_answer";
    public static final String BUNDLE_EXTRA_RESPONSE_ID = "response_id";
    public static final String BUNDLE_EXTRA_LEARN_MORE_URL = "learn_more_url";
    public static final String BUNDLE_EXTRA_PERSONAL = "is_personal";
    public static final String BUNDLE_EXTRA_SUGGESTION_TYPE = "suggestion_type";
    public static final String BUNDLE_EXTRA_SUGGEST_RENDER_TEXT = "suggest_render_text";
    public static final String BUNDLE_EXTRA_ZERO_STATE_CACHE = "zero_state_cache";
    public static final String BUNDLE_EXTRA_TALL_CARD_HEADER = "tall_card_header";
    public static final String BUNDLE_EXTRA_TALL_CARD_IMAGE_DESCRIPTION =
            "tall_card_image_description";
    public static final String BUNDLE_EXTRA_BITMAP_URL = "bitmap_url";

    // Used for web suggestions count for both AA+ and QSB entry point.
    // Returns the number of web suggestions to be shown.
    public static final String WEB_SUG_COUNT = "web_sug_count";

    /**
     *  Flag to control whether thumbnail(s) should fill the thumbnail container's width or not.
     *  When this flag is true, when there are less than the maximum number of thumbnails in the
     *  container, the thumbnails will stretch to fill the container's width.
     *  When this flag is false, thumbnails will always be cropped to a square ratio even if
     *  there aren't enough thumbnails to fill the container.
     *
     *  Only relevant in {@link LayoutType#THUMBNAIL_CONTAINER} and {@link LayoutType#THUMBNAIL}.
     */
    public static final String BUNDLE_EXTRA_SHOULD_FILL_CONTAINER_WIDTH =
            "should_fill_container_width";
    /**
     *  Flag to control whether the SearchTarget's label should be hidden.
     *  When this flag is true, label will be hidden.
     *  When this flag is false (or omitted), {@link SearchAction#mTitle} will be shown.
     */
    public static final String BUNDLE_EXTRA_HIDE_LABEL =
            "hide_label";
    public static final String BUNDLE_EXTRA_SUGGESTION_ACTION_TEXT = "suggestion_action_text";
    public static final String BUNDLE_EXTRA_SUGGESTION_ACTION_RPC = "suggestion_action_rpc";
    public static final String BUNDLE_EXTRA_SUPPORT_QUERY_BUILDER = "support_query_builder";
    public static final String BUNDLE_EXTRA_SUGGEST_RAW_TEXT = "suggest_raw_text";
    public static final String BUNDLE_EXTRA_SUGGEST_TRUNCATE_START = "suggest_truncate_start";

    /** Web data related helper methods */
    public static boolean isEntity(@Nullable SearchTarget target) {
        return target != null && target.getExtras() != null
                && target.getExtras().getBoolean(BUNDLE_EXTRA_ENTITY);
    }

    public static boolean isAnswer(@Nullable SearchTarget target) {
        return target != null && target.getExtras() != null
                && target.getExtras().getBoolean(BUNDLE_EXTRA_ANSWER);
    }

    /** Whether the search target is a rich answer web result. */
    public static boolean isRichAnswer(@Nullable SearchTarget target) {
        return target != null && isAnswer(target)
                && target.getLayoutType().equals(TALL_CARD_WITH_IMAGE_NO_ICON);
    }
}
