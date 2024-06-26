/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.monet;

import android.util.Pair;

import com.google.ux.material.libmonet.dynamiccolor.DynamicColor;
import com.google.ux.material.libmonet.dynamiccolor.MaterialDynamicColors;

import java.util.ArrayList;
import java.util.List;

public class DynamicColors {

    /**
     * List of all public Dynamic Color (Light and Dark) resources
     *
     * @param isExtendedFidelity boolean indicating if Fidelity is active
     * @return List of pairs of Resource Names / DynamicColor
     */
    public static List<Pair<String, DynamicColor>> getAllDynamicColorsMapped(
            boolean isExtendedFidelity) {
        MaterialDynamicColors mdc = new MaterialDynamicColors(isExtendedFidelity);
        List<Pair<String, DynamicColor>> list = new ArrayList<>();
        list.add(Pair.create("primary_container", mdc.primaryContainer()));
        list.add(Pair.create("on_primary_container", mdc.onPrimaryContainer()));
        list.add(Pair.create("primary", mdc.primary()));
        list.add(Pair.create("on_primary", mdc.onPrimary()));
        list.add(Pair.create("secondary_container", mdc.secondaryContainer()));
        list.add(Pair.create("on_secondary_container", mdc.onSecondaryContainer()));
        list.add(Pair.create("secondary", mdc.secondary()));
        list.add(Pair.create("on_secondary", mdc.onSecondary()));
        list.add(Pair.create("tertiary_container", mdc.tertiaryContainer()));
        list.add(Pair.create("on_tertiary_container", mdc.onTertiaryContainer()));
        list.add(Pair.create("tertiary", mdc.tertiary()));
        list.add(Pair.create("on_tertiary", mdc.onTertiary()));
        list.add(Pair.create("background", mdc.background()));
        list.add(Pair.create("on_background", mdc.onBackground()));
        list.add(Pair.create("surface", mdc.surface()));
        list.add(Pair.create("on_surface", mdc.onSurface()));
        list.add(Pair.create("surface_container_low", mdc.surfaceContainerLow()));
        list.add(Pair.create("surface_container_lowest", mdc.surfaceContainerLowest()));
        list.add(Pair.create("surface_container", mdc.surfaceContainer()));
        list.add(Pair.create("surface_container_high", mdc.surfaceContainerHigh()));
        list.add(Pair.create("surface_container_highest", mdc.surfaceContainerHighest()));
        list.add(Pair.create("surface_bright", mdc.surfaceBright()));
        list.add(Pair.create("surface_dim", mdc.surfaceDim()));
        list.add(Pair.create("surface_variant", mdc.surfaceVariant()));
        list.add(Pair.create("on_surface_variant", mdc.onSurfaceVariant()));
        list.add(Pair.create("outline", mdc.outline()));
        list.add(Pair.create("outline_variant", mdc.outlineVariant()));
        list.add(Pair.create("error", mdc.error()));
        list.add(Pair.create("on_error", mdc.onError()));
        list.add(Pair.create("error_container", mdc.errorContainer()));
        list.add(Pair.create("on_error_container", mdc.onErrorContainer()));
        list.add(Pair.create("control_activated", mdc.controlActivated()));
        list.add(Pair.create("control_normal", mdc.controlNormal()));
        list.add(Pair.create("control_highlight", mdc.controlHighlight()));
        list.add(Pair.create("text_primary_inverse", mdc.textPrimaryInverse()));
        list.add(Pair.create("text_secondary_and_tertiary_inverse",
                mdc.textSecondaryAndTertiaryInverse()));
        list.add(Pair.create("text_primary_inverse_disable_only",
                mdc.textPrimaryInverseDisableOnly()));
        list.add(Pair.create("text_secondary_and_tertiary_inverse_disabled",
                mdc.textSecondaryAndTertiaryInverseDisabled()));
        list.add(Pair.create("text_hint_inverse", mdc.textHintInverse()));
        list.add(Pair.create("palette_key_color_primary", mdc.primaryPaletteKeyColor()));
        list.add(Pair.create("palette_key_color_secondary", mdc.secondaryPaletteKeyColor()));
        list.add(Pair.create("palette_key_color_tertiary", mdc.tertiaryPaletteKeyColor()));
        list.add(Pair.create("palette_key_color_neutral", mdc.neutralPaletteKeyColor()));
        list.add(Pair.create("palette_key_color_neutral_variant",
                mdc.neutralVariantPaletteKeyColor()));
        return list;
    }

    /**
     * List of all public Static Color resources
     *
     * @param isExtendedFidelity boolean indicating if Fidelity is active
     * @return List of pairs of Resource Names / DynamicColor @return
     */
    public static List<Pair<String, DynamicColor>> getFixedColorsMapped(
            boolean isExtendedFidelity) {
        MaterialDynamicColors mdc = new MaterialDynamicColors(isExtendedFidelity);
        List<Pair<String, DynamicColor>> list = new ArrayList<>();
        list.add(Pair.create("primary_fixed", mdc.primaryFixed()));
        list.add(Pair.create("primary_fixed_dim", mdc.primaryFixedDim()));
        list.add(Pair.create("on_primary_fixed", mdc.onPrimaryFixed()));
        list.add(Pair.create("on_primary_fixed_variant", mdc.onPrimaryFixedVariant()));
        list.add(Pair.create("secondary_fixed", mdc.secondaryFixed()));
        list.add(Pair.create("secondary_fixed_dim", mdc.secondaryFixedDim()));
        list.add(Pair.create("on_secondary_fixed", mdc.onSecondaryFixed()));
        list.add(Pair.create("on_secondary_fixed_variant", mdc.onSecondaryFixedVariant()));
        list.add(Pair.create("tertiary_fixed", mdc.tertiaryFixed()));
        list.add(Pair.create("tertiary_fixed_dim", mdc.tertiaryFixedDim()));
        list.add(Pair.create("on_tertiary_fixed", mdc.onTertiaryFixed()));
        list.add(Pair.create("on_tertiary_fixed_variant", mdc.onTertiaryFixedVariant()));
        return list;
    }


    /**
     * List of all private SystemUI Color resources
     *
     * @param isExtendedFidelity boolean indicating if Fidelity is active
     * @return List of pairs of Resource Names / DynamicColor
     */
    public static List<Pair<String, DynamicColor>> getCustomColorsMapped(
            boolean isExtendedFidelity) {
        CustomDynamicColors customMdc = new CustomDynamicColors(isExtendedFidelity);
        List<Pair<String, DynamicColor>> list = new ArrayList<>();
        list.add(Pair.create("widget_background", customMdc.widgetBackground()));
        list.add(Pair.create("clock_hour", customMdc.clockHour()));
        list.add(Pair.create("clock_minute", customMdc.clockMinute()));
        list.add(Pair.create("clock_second", customMdc.weatherTemp()));
        list.add(Pair.create("theme_app", customMdc.themeApp()));
        list.add(Pair.create("on_theme_app", customMdc.onThemeApp()));
        list.add(Pair.create("theme_app_ring", customMdc.themeAppRing()));
        list.add(Pair.create("theme_notif", customMdc.themeNotif()));
        list.add(Pair.create("brand_a", customMdc.brandA()));
        list.add(Pair.create("brand_b", customMdc.brandB()));
        list.add(Pair.create("brand_c", customMdc.brandC()));
        list.add(Pair.create("brand_d", customMdc.brandD()));
        list.add(Pair.create("under_surface", customMdc.underSurface()));
        list.add(Pair.create("shade_active", customMdc.shadeActive()));
        list.add(Pair.create("on_shade_active", customMdc.onShadeActive()));
        list.add(Pair.create("on_shade_active_variant", customMdc.onShadeActiveVariant()));
        list.add(Pair.create("shade_inactive", customMdc.shadeInactive()));
        list.add(Pair.create("on_shade_inactive", customMdc.onShadeInactive()));
        list.add(Pair.create("on_shade_inactive_variant", customMdc.onShadeInactiveVariant()));
        list.add(Pair.create("shade_disabled", customMdc.shadeDisabled()));
        list.add(Pair.create("overview_background", customMdc.overviewBackground()));
        return list;
    }

}
