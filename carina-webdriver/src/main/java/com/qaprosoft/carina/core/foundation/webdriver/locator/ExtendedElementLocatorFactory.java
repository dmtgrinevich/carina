/*******************************************************************************
 * Copyright 2020-2022 Zebrunner Inc (https://www.zebrunner.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.qaprosoft.carina.core.foundation.webdriver.locator;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.support.FindAll;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.FindBys;
import org.openqa.selenium.support.pagefactory.AbstractAnnotations;
import org.openqa.selenium.support.pagefactory.Annotations;
import org.openqa.selenium.support.pagefactory.ElementLocator;
import org.openqa.selenium.support.pagefactory.ElementLocatorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.qaprosoft.carina.core.foundation.webdriver.IDriverPool;
import com.qaprosoft.carina.core.foundation.webdriver.decorator.annotations.AccessibilityId;
import com.qaprosoft.carina.core.foundation.webdriver.decorator.annotations.ClassChain;
import com.qaprosoft.carina.core.foundation.webdriver.decorator.annotations.Predicate;
import com.zebrunner.carina.utils.commons.SpecialKeywords;

import io.appium.java_client.internal.CapabilityHelpers;
import io.appium.java_client.pagefactory.DefaultElementByBuilder;
import io.appium.java_client.remote.MobileCapabilityType;

public final class ExtendedElementLocatorFactory implements ElementLocatorFactory, IDriverPool {
    static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final SearchContext searchContext;
    private final WebDriver webDriver;
    private final String platform;
    private final String automation;
    private final String driverType;
    // for case insensitive. refactor/remove if can
    private boolean isMobileApp = false;

    public ExtendedElementLocatorFactory(WebDriver webDriver, SearchContext searchContext) {
        this.webDriver = webDriver;
        this.searchContext = searchContext;
        if (this.webDriver instanceof HasCapabilities) {
            Capabilities capabilities = ((HasCapabilities) this.webDriver).getCapabilities();
            this.platform = CapabilityHelpers.getCapability(capabilities, CapabilityType.PLATFORM_NAME, String.class);
            this.automation = CapabilityHelpers.getCapability(capabilities, MobileCapabilityType.AUTOMATION_NAME, String.class);
            String browserName = CapabilityHelpers.getCapability(capabilities, CapabilityType.BROWSER_NAME, String.class);
            isMobileApp = CapabilityHelpers.getCapability(capabilities, MobileCapabilityType.APP, String.class) != null;
            if (SpecialKeywords.ANDROID.equalsIgnoreCase(platform) ||
                    SpecialKeywords.IOS.equalsIgnoreCase(platform) ||
                    SpecialKeywords.TVOS.equalsIgnoreCase(platform)) {
                driverType = SpecialKeywords.MOBILE;
            } else if (!StringUtils.isEmpty(browserName)) {
                driverType = SpecialKeywords.DESKTOP;
            } else if (SpecialKeywords.WINDOWS.equalsIgnoreCase(platform)) {
                driverType = SpecialKeywords.WINDOWS;
            } else if (SpecialKeywords.MAC.equalsIgnoreCase(platform)) {
                driverType = SpecialKeywords.MAC;
            } else {
                driverType = SpecialKeywords.DESKTOP;
            }
        } else {
            this.platform = null;
            this.automation = null;
            // todo ?
            this.driverType = SpecialKeywords.DESKTOP;
        }
    }

    public ElementLocator createLocator(Field field) {
        AbstractAnnotations annotations = null;
        if (!SpecialKeywords.DESKTOP.equals(driverType)) {
            // todo create Annotations for every type of annotations
            if (field.isAnnotationPresent(ExtendedFindBy.class) ||
                    field.isAnnotationPresent(ClassChain.class) ||
                    field.isAnnotationPresent(AccessibilityId.class) ||
                    field.isAnnotationPresent(Predicate.class)) {
                annotations = new ExtendedAnnotations(field, platform);
            } else {
                    DefaultElementByBuilder builder = new DefaultElementByBuilder(platform, automation);
                    builder.setAnnotated(field);
                    annotations = builder;
            }
        } else if (field.getAnnotation(FindBy.class) != null ||
                    field.getAnnotation(FindBys.class) != null ||
                    field.getAnnotation(FindAll.class) != null) {
                annotations = new Annotations(field);
        }

        if (annotations == null) {
            return null;
        }
        ExtendedElementLocator extendedElementLocator = null;
        try {
            extendedElementLocator = new ExtendedElementLocator(webDriver, searchContext, field, annotations, isMobileApp);
        } catch (Exception e) {
            LOGGER.debug("Cannot create extended element locator", e);
        }
        return extendedElementLocator;
    }
}