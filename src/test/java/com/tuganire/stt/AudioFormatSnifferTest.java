package com.tuganire.stt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AudioFormatSnifferTest {

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    void detectsWebmFromEbmlHeader() {
        assertThat(AudioFormatSniffer.extension(new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0, 0}))
                .isEqualTo("webm");
    }

    @Test
    void detectsMp4FromFtypBox() {
        assertThat(AudioFormatSniffer.extension(ascii("\0\0\0 ftypM4A "))).isEqualTo("mp4");
    }

    @Test
    void detectsWavOggFlacAndMp3() {
        assertThat(AudioFormatSniffer.extension(ascii("RIFF....WAVE"))).isEqualTo("wav");
        assertThat(AudioFormatSniffer.extension(ascii("OggS...."))).isEqualTo("ogg");
        assertThat(AudioFormatSniffer.extension(ascii("fLaC...."))).isEqualTo("flac");
        assertThat(AudioFormatSniffer.extension(ascii("ID3....."))).isEqualTo("mp3");
        assertThat(AudioFormatSniffer.extension(new byte[]{(byte) 0xFF, (byte) 0xFB, 0x10, 0x00})).isEqualTo("mp3");
    }

    @Test
    void fallsBackToWavForUnknownOrTinyInput() {
        assertThat(AudioFormatSniffer.extension(null)).isEqualTo("wav");
        assertThat(AudioFormatSniffer.extension(new byte[]{1, 2})).isEqualTo("wav");
        assertThat(AudioFormatSniffer.extension(ascii("garbage!"))).isEqualTo("wav");
    }
}
