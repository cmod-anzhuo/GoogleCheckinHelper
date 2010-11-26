LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := eng

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := GoogleCheckinHelper

#LOCAL_PROGUARD_ENABLED := full
#LOCAL_PROGUARD_CUSTOM := -keep class com.hiapk.updater.UpdateInfo \{ public *\;\}
#LOCAL_STATIC_JAVA_LIBRARIES := google-framework

LOCAL_CERTIFICATE := platform

include $(BUILD_PACKAGE)
