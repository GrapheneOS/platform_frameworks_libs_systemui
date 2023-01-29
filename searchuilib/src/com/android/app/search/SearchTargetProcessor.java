package com.android.app.search;

import static com.android.app.search.LayoutType.EMPTY_DIVIDER;
import static com.android.app.search.LayoutType.ICON_HORIZONTAL_TEXT;
import static com.android.app.search.LayoutType.ICON_SINGLE_VERTICAL_TEXT;
import static com.android.app.search.LayoutType.TEXT_HEADER_ROW;
import static com.android.app.search.ResultType.APPLICATION;
import static com.android.app.search.ResultType.NO_FULFILLMENT;
import static com.android.app.search.ResultType.SUGGEST;
import static com.android.app.search.SearchTargetExtras.BUNDLE_EXTRA_PROXY_WEB_ITEM;
import static com.android.app.search.SearchTargetExtras.BUNDLE_EXTRA_QUICK_LAUNCH;
import static com.android.app.search.SearchTargetExtras.getDecoratorId;
import static com.android.app.search.SearchTargetExtras.isAnswer;
import static com.android.app.search.SearchTargetExtras.isRichAnswer;
import static com.android.app.search.SearchTargetGenerator.EMPTY_DIVIDER_TARGET;
import static com.android.app.search.SearchTargetGenerator.SECTION_HEADER_TARGET;

import android.app.search.SearchTarget;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class SearchTargetProcessor {

    /**
     * Handles light merging of the web targets and the on device targets.
     *
     * @param webTargets list of results that arrive for the web blocks
     * @param deviceTargets list of results that arrive for on device blocks
     * @param isForQsb {@code true} when merge logic should be applied to home searchbox
     * @param qsbWebCount count of web results if QSB entry
     * @param allappsWebCnt web result count when there are on device result, on all apps
     * @param useFallbackAppSearch {@code true} if app corpus should be generated
     * @return merged {@link SearchTarget} array list
     */
    public static ArrayList<SearchTarget> injectSuggestResultsMiddle(
            @NonNull ArrayList<SearchTarget> webTargets,
            @NonNull ArrayList<SearchTarget> deviceTargets,
            boolean isForQsb,
            int qsbWebCount,
            int allappsWebCnt,
            boolean useFallbackAppSearch) {

        ArrayList<SearchTarget> tmpTargets = new ArrayList<>();
        // Identify insertion index, here we insert immediately after last app row
        // OR last app block.
        int insertionIdx = 0;
        int placeholderIdx = -1;
        int richAnswerPlaceholderIdx = -1;
        String lastAppBlockTargetId = "";
        boolean hasTextHeader = false;

        if (webTargets.size() > 0) {
            webTargets.get(0).getExtras().putBoolean(BUNDLE_EXTRA_PROXY_WEB_ITEM, true);
        }

        for (int i = 0; i < deviceTargets.size(); i++) {
            if (deviceTargets.get(i).getLayoutType().equals(TEXT_HEADER_ROW)) {
                hasTextHeader = true;
            }
            // Rich answer should always be above web block, so check and remove the rich answer
            // placeholder first.
            if (deviceTargets.get(i).getLayoutType().equals("richanswer_placeholder")) {
                richAnswerPlaceholderIdx = i;
                removePlaceholder(deviceTargets, richAnswerPlaceholderIdx);
            }
            if (deviceTargets.size() > i && deviceTargets.get(i).getLayoutType().equals(
                    "placeholder")) {
                insertionIdx = placeholderIdx = i;
                removePlaceholder(deviceTargets, placeholderIdx);
            }
        }

        // If device target exists and if there's any app block, we need to find the insertion point
        if (placeholderIdx < 0 && deviceTargets.size() > 0
                && deviceTargets.get(0).getResultType() == APPLICATION) {
            // Scan and get last app corpus block index
            for (int i = 0; i < deviceTargets.size(); i++) {
                SearchTarget t = deviceTargets.get(i);
                if (lastAppBlockTargetId.equals(t.getParentId())
                        || lastAppBlockTargetId.equals(getDecoratorId(t))
                        || t.getLayoutType().equals(EMPTY_DIVIDER)) {
                    continue;
                }
                if (t.getResultType() == APPLICATION
                        && (t.getLayoutType().equals(ICON_HORIZONTAL_TEXT)
                        || t.getLayoutType().equals(ICON_SINGLE_VERTICAL_TEXT))) {
                    lastAppBlockTargetId = t.getId();
                    insertionIdx = i + 1;
                } else {
                    insertionIdx = i;
                    break;
                }
            }
        }

        if (useFallbackAppSearch && deviceTargets.size() > 0) {
            insertionIdx = deviceTargets.size() + 1;
            deviceTargets.get(0).getExtras().putBoolean(BUNDLE_EXTRA_QUICK_LAUNCH, true);
            deviceTargets.add(EMPTY_DIVIDER_TARGET);
        }

        SearchTarget divider = EMPTY_DIVIDER_TARGET;
        int fallbackCount = getFallbackAndDividerCount(deviceTargets);

        if (!hasTextHeader) {
            // Add a section header for on device results if there are targets other than apps,
            // dividers and fallback.
            if (!useFallbackAppSearch && deviceTargets.size() - insertionIdx > fallbackCount) {
                divider = SECTION_HEADER_TARGET;
            }
        }
        // Composit the device and web result / insert divider
        tmpTargets.addAll(deviceTargets);

        if (!webTargets.isEmpty() && isRichAnswer(webTargets.get(0))) {
            allappsWebCnt++;
        }
        boolean nonFallbackTargetsExists = deviceTargets.size() > fallbackCount;
        if (isForQsb) {
            tmpTargets.addAll(insertionIdx, webTargets.subList(0, qsbWebCount));
            tmpTargets.add(insertionIdx + qsbWebCount, divider);
        } else if (nonFallbackTargetsExists && webTargets.size() >= allappsWebCnt) {
            tmpTargets.addAll(insertionIdx, webTargets.subList(0, allappsWebCnt));
            tmpTargets.add(insertionIdx + allappsWebCnt, divider);
        } else {
            if (deviceTargets.size() > 0 && webTargets.size() > 0) {
                tmpTargets.add(insertionIdx, EMPTY_DIVIDER_TARGET); // divider before fallback
            }
            tmpTargets.addAll(insertionIdx, webTargets);
        }
        if (webTargets.size() > 1 && isAnswer(webTargets.get(0))) {
            SearchTarget topWebTarget = webTargets.get(0);
            if (richAnswerPlaceholderIdx == -1 || !isRichAnswer(topWebTarget)) {
                // When rich answer placeholder is not set, or there's no rich answer in the list,
                // keep answer on top of web list and put a divider below.
                tmpTargets.add(insertionIdx + 1, EMPTY_DIVIDER_TARGET);
            } else {
                // Remove the answer from top of target list, then add to the placeholder position.
                tmpTargets.remove(insertionIdx);
                tmpTargets.add(richAnswerPlaceholderIdx, topWebTarget);
                tmpTargets.add(richAnswerPlaceholderIdx + 1, EMPTY_DIVIDER_TARGET);
            }
        }
        return tmpTargets;
    }


    /**
     * Count the fallback and dividers targets.
     * - Fallback targets have resultType == SUGGEST
     * - Dividers have resultType == NO_FULFILLMENT
     */
    public static int getFallbackAndDividerCount(ArrayList<SearchTarget> deviceTargets) {
        int fallback = 0;
        for (int i = 0; i < deviceTargets.size(); i++) {
            int resultType = deviceTargets.get(i).getResultType();
            if (resultType == SUGGEST || resultType == NO_FULFILLMENT) {
                fallback++;
            }
        }
        return fallback;
    }

    private static void removePlaceholder(List<SearchTarget> deviceTargets, int placeholderIdx) {
        deviceTargets.remove(placeholderIdx); // remove placeholder
        if (deviceTargets.size() > placeholderIdx) {
            String layoutType = deviceTargets.get(placeholderIdx).getLayoutType();
            if (layoutType.equals(EMPTY_DIVIDER)) {
                deviceTargets.remove(placeholderIdx); // remove the subsequent divider
            }
        }
    }

}
