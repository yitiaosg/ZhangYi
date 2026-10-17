# 第三方依赖许可证与版权声明

账易自身的原创代码与文档采用根目录的 MIT 许可。第三方组件继续适用各自许可证，不因项目使用 MIT 而变更。

本清单依据实际解析的 Release 运行时、JVM 测试和 Android 仪器测试依赖生成，共104项。Maven POM 未声明许可的 Guava ListenableFuture、javax.inject 和 Hamcrest，另核对了对应版本的源码/父POM与随包许可证；证据类型见 `docs/dependency-licenses.json`。

- Release 运行依赖：Apache-2.0 与 BSD-3-Clause，保留原许可和版权声明后可与 MIT 项目共同发布。
- JUnit：EPL-1.0，仅为测试依赖，不进入正式APK；并未改动或复制其实现到应用源代码。
- Hamcrest：BSD-3-Clause，测试使用。
- 依赖的可再分发许可全文位于 `LICENSES/`；运行时声明与许可也随APK放在 `assets/open-source/`。
- Gradle Wrapper 与构建工具保持其 Apache-2.0 许可，原脚本许可头不作删除。GitHub Actions 引用外部项目，不将其实现复制到 App。
- 未重新许可、修改或抹去任何依赖的版权信息。后续更改依赖版本时应重新核对本清单及随包许可。

## 明确版权声明

- AndroidX：Copyright The Android Open Source Project，依各组件原声明。
- Kotlin / kotlinx：Copyright JetBrains s.r.o. and Kotlin Programming Language contributors，依各组件原声明。
- Protobuf（DataStore shaded runtime）：Copyright 2008 Google Inc. All rights reserved. 详见 `LICENSES/BSD-3-Clause-Protobuf.txt`。
- Guava ListenableFuture 1.0：Copyright (C) 2007 The Guava Authors，Apache-2.0。
- javax.inject 1：Copyright (C) 2009 The JSR-330 Expert Group，Apache-2.0。
- Hamcrest 1.3：Copyright (c) 2000-2006, www.hamcrest.org. All rights reserved. 详见其原始许可证。
- Gradle：详见 `LICENSES/Gradle-NOTICE.txt`。

## 依赖版本表

R = 正式运行时；J = JVM测试；A = Android仪器测试。测试依赖不表示会打入正式APK。

| 组件 | 许可 | 范围 |
|---|---|---|
| `androidx.activity:activity-compose:1.10.1` | Apache-2.0 | R / J / A |
| `androidx.activity:activity-ktx:1.10.1` | Apache-2.0 | R / J / A |
| `androidx.activity:activity:1.10.1` | Apache-2.0 | R / J / A |
| `androidx.annotation:annotation-experimental:1.4.1` | Apache-2.0 | R / J / A |
| `androidx.annotation:annotation-jvm:1.9.1` | Apache-2.0 | R / J / A |
| `androidx.arch.core:core-common:2.2.0` | Apache-2.0 | R / J / A |
| `androidx.arch.core:core-runtime:2.2.0` | Apache-2.0 | R / J / A |
| `androidx.autofill:autofill:1.0.0` | Apache-2.0 | R / J / A |
| `androidx.collection:collection-jvm:1.5.0` | Apache-2.0 | R / J / A |
| `androidx.collection:collection-ktx:1.5.0` | Apache-2.0 | R / J / A |
| `androidx.compose.animation:animation-android:1.8.0` | Apache-2.0 | R / J |
| `androidx.compose.animation:animation-core-android:1.8.0` | Apache-2.0 | R / J |
| `androidx.compose.foundation:foundation-android:1.8.0` | Apache-2.0 | R / J |
| `androidx.compose.foundation:foundation-layout-android:1.8.0` | Apache-2.0 | R / J |
| `androidx.compose.material3:material3-android:1.3.2` | Apache-2.0 | R / J |
| `androidx.compose.material:material-android:1.8.0` | Apache-2.0 | J |
| `androidx.compose.material:material-icons-core-android:1.7.8` | Apache-2.0 | R / J |
| `androidx.compose.material:material-ripple-android:1.8.0` | Apache-2.0 | R / J |
| `androidx.compose.runtime:runtime-android:1.8.0` | Apache-2.0 | R / J / A |
| `androidx.compose.runtime:runtime-saveable-android:1.8.0` | Apache-2.0 | R / J / A |
| `androidx.compose.ui:ui-android:1.8.0` | Apache-2.0 | R / J / A |
| `androidx.compose.ui:ui-geometry-android:1.8.0` | Apache-2.0 | R / J / A |
| `androidx.compose.ui:ui-graphics-android:1.8.0` | Apache-2.0 | R / J / A |
| `androidx.compose.ui:ui-test-android:1.8.0` | Apache-2.0 | A |
| `androidx.compose.ui:ui-test-junit4-android:1.8.0` | Apache-2.0 | A |
| `androidx.compose.ui:ui-test-manifest:1.8.0` | Apache-2.0 | J |
| `androidx.compose.ui:ui-text-android:1.8.0` | Apache-2.0 | R / J / A |
| `androidx.compose.ui:ui-tooling-android:1.8.0` | Apache-2.0 | J |
| `androidx.compose.ui:ui-tooling-data-android:1.8.0` | Apache-2.0 | J |
| `androidx.compose.ui:ui-tooling-preview-android:1.8.0` | Apache-2.0 | R / J |
| `androidx.compose.ui:ui-unit-android:1.8.0` | Apache-2.0 | R / J / A |
| `androidx.compose.ui:ui-util-android:1.8.0` | Apache-2.0 | R / J / A |
| `androidx.concurrent:concurrent-futures-ktx:1.1.0` | Apache-2.0 | A |
| `androidx.concurrent:concurrent-futures:1.1.0` | Apache-2.0 | R / J / A |
| `androidx.core:core-ktx:1.16.0` | Apache-2.0 | R / J / A |
| `androidx.core:core-viewtree:1.0.0` | Apache-2.0 | R / J / A |
| `androidx.core:core:1.16.0` | Apache-2.0 | R / J / A |
| `androidx.customview:customview-poolingcontainer:1.0.0` | Apache-2.0 | R / J / A |
| `androidx.datastore:datastore-android:1.1.4` | Apache-2.0 | R / J |
| `androidx.datastore:datastore-core-android:1.1.4` | Apache-2.0 | R / J |
| `androidx.datastore:datastore-core-okio-jvm:1.1.4` | Apache-2.0 | R / J |
| `androidx.datastore:datastore-preferences-android:1.1.4` | Apache-2.0 | R / J |
| `androidx.datastore:datastore-preferences-core-jvm:1.1.4` | Apache-2.0 | R / J |
| `androidx.datastore:datastore-preferences-external-protobuf:1.1.4` | BSD-3-Clause | R / J |
| `androidx.datastore:datastore-preferences-proto:1.1.4` | Apache-2.0 | R / J |
| `androidx.emoji2:emoji2:1.4.0` | Apache-2.0 | R / J / A |
| `androidx.graphics:graphics-path:1.0.1` | Apache-2.0 | R / J / A |
| `androidx.interpolator:interpolator:1.0.0` | Apache-2.0 | R / J / A |
| `androidx.lifecycle:lifecycle-common-java8:2.9.0` | Apache-2.0 | R / J |
| `androidx.lifecycle:lifecycle-common-jvm:2.9.0` | Apache-2.0 | R / J / A |
| `androidx.lifecycle:lifecycle-livedata-core:2.9.0` | Apache-2.0 | R / J / A |
| `androidx.lifecycle:lifecycle-process:2.9.0` | Apache-2.0 | R / J / A |
| `androidx.lifecycle:lifecycle-runtime-android:2.9.0` | Apache-2.0 | R / J / A |
| `androidx.lifecycle:lifecycle-runtime-compose-android:2.9.0` | Apache-2.0 | R / J / A |
| `androidx.lifecycle:lifecycle-runtime-ktx-android:2.9.0` | Apache-2.0 | R / J / A |
| `androidx.lifecycle:lifecycle-viewmodel-android:2.9.0` | Apache-2.0 | R / J / A |
| `androidx.lifecycle:lifecycle-viewmodel-compose-android:2.9.0` | Apache-2.0 | R / J |
| `androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0` | Apache-2.0 | R / J / A |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate-android:2.9.0` | Apache-2.0 | R / J / A |
| `androidx.navigation:navigation-common-ktx:2.8.9` | Apache-2.0 | R / J |
| `androidx.navigation:navigation-common:2.8.9` | Apache-2.0 | R / J |
| `androidx.navigation:navigation-compose:2.8.9` | Apache-2.0 | R / J |
| `androidx.navigation:navigation-runtime-ktx:2.8.9` | Apache-2.0 | R / J |
| `androidx.navigation:navigation-runtime:2.8.9` | Apache-2.0 | R / J |
| `androidx.profileinstaller:profileinstaller:1.4.0` | Apache-2.0 | R / J / A |
| `androidx.room:room-common-jvm:2.7.1` | Apache-2.0 | R / J / A |
| `androidx.room:room-ktx:2.7.1` | Apache-2.0 | R / J |
| `androidx.room:room-migration-jvm:2.7.1` | Apache-2.0 | A |
| `androidx.room:room-runtime-android:2.7.1` | Apache-2.0 | R / J / A |
| `androidx.room:room-testing-android:2.7.1` | Apache-2.0 | A |
| `androidx.savedstate:savedstate-android:1.3.0` | Apache-2.0 | R / J / A |
| `androidx.savedstate:savedstate-ktx:1.3.0` | Apache-2.0 | R / J / A |
| `androidx.sqlite:sqlite-android:2.5.0` | Apache-2.0 | R / J / A |
| `androidx.sqlite:sqlite-framework-android:2.5.0` | Apache-2.0 | R / J / A |
| `androidx.startup:startup-runtime:1.1.1` | Apache-2.0 | R / J / A |
| `androidx.test.espresso:espresso-core:3.6.1` | Apache-2.0 | A |
| `androidx.test.espresso:espresso-idling-resource:3.6.1` | Apache-2.0 | A |
| `androidx.test.ext:junit:1.2.1` | Apache-2.0 | A |
| `androidx.test.services:storage:1.5.0` | Apache-2.0 | A |
| `androidx.test:core:1.6.1` | Apache-2.0 | A |
| `androidx.test:monitor:1.7.2` | Apache-2.0 | A |
| `androidx.test:runner:1.6.2` | Apache-2.0 | A |
| `androidx.tracing:tracing:1.2.0` | Apache-2.0 | R / J / A |
| `androidx.versionedparcelable:versionedparcelable:1.1.1` | Apache-2.0 | R / J / A |
| `com.google.code.findbugs:jsr305:3.0.2` | Apache-2.0 | A |
| `com.google.errorprone:error_prone_annotations:2.18.0` | Apache-2.0 | A |
| `com.google.guava:listenablefuture:1.0` | Apache-2.0 | R / J / A |
| `com.squareup.okio:okio-jvm:3.4.0` | Apache-2.0 | R / J |
| `javax.inject:javax.inject:1` | Apache-2.0 | A |
| `junit:junit:4.13.2` | EPL-1.0 | J / A |
| `org.hamcrest:hamcrest-core:1.3` | BSD-3-Clause | J / A |
| `org.hamcrest:hamcrest-library:1.3` | BSD-3-Clause | A |
| `org.jetbrains.kotlin:kotlin-android-extensions-runtime:1.9.22` | Apache-2.0 | R / J |
| `org.jetbrains.kotlin:kotlin-parcelize-runtime:1.9.22` | Apache-2.0 | R / J |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.0` | Apache-2.0 | R / J |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.0` | Apache-2.0 | R / J |
| `org.jetbrains.kotlin:kotlin-stdlib:2.1.20` | Apache-2.0 | R / J / A |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2` | Apache-2.0 | R / J / A |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2` | Apache-2.0 | R / J / A |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.10.2` | Apache-2.0 | J / A |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1` | Apache-2.0 | R / J / A |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.8.1` | Apache-2.0 | R / J / A |
| `org.jetbrains:annotations:23.0.0` | Apache-2.0 | R / J / A |
| `org.jspecify:jspecify:1.0.0` | Apache-2.0 | R / J / A |
