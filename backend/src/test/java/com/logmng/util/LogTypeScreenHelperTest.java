package com.logmng.util;

import com.logmng.constants.ScreenConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for LogTypeScreenHelper (logType ↔ screen_id). Req 20260318.
 */
class LogTypeScreenHelperTest {

    @Test
    void screenIdForLogType_pb_feplog_returnsPbFeplog() {
        assertThat(LogTypeScreenHelper.screenIdForLogType("pb_feplog")).isEqualTo(ScreenConstants.PB_FEPLOG);
        assertThat(LogTypeScreenHelper.screenIdForLogType(" pb_feplog ")).isEqualTo(ScreenConstants.PB_FEPLOG);
    }

    @Test
    void screenIdForLogType_java_fw_imglog_returnsJavaFwImagelog() {
        assertThat(LogTypeScreenHelper.screenIdForLogType("java_fw_imglog")).isEqualTo(ScreenConstants.JAVA_FW_IMAGELOG);
        assertThat(LogTypeScreenHelper.screenIdForLogType(" java_fw_imglog ")).isEqualTo(ScreenConstants.JAVA_FW_IMAGELOG);
    }

    @Test
    void screenIdForLogType_unknown_returnsNull() {
        assertThat(LogTypeScreenHelper.screenIdForLogType("unknown")).isNull();
        assertThat(LogTypeScreenHelper.screenIdForLogType("")).isNull();
        assertThat(LogTypeScreenHelper.screenIdForLogType(null)).isNull();
    }

    @Test
    void isKnownLogType_returnsTrueForKnown() {
        assertThat(LogTypeScreenHelper.isKnownLogType("pb_feplog")).isTrue();
        assertThat(LogTypeScreenHelper.isKnownLogType("java_fw_imglog")).isTrue();
    }

    @Test
    void isKnownLogType_returnsFalseForUnknown() {
        assertThat(LogTypeScreenHelper.isKnownLogType("other")).isFalse();
        assertThat(LogTypeScreenHelper.isKnownLogType("")).isFalse();
        assertThat(LogTypeScreenHelper.isKnownLogType(null)).isFalse();
    }
}
