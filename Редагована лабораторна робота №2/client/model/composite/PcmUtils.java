package com.ivanka.audioeditor.client.model.composite;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class PcmUtils {

    public static AudioFormat getStandardFormat() {
        return new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                44100f,
                16,
                2,
                4,
                44100f,
                false
        );
    }

    public static float[][] readWavStereo(File wavFile, AudioFormat[] fmtOut) throws Exception {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(wavFile)) {
            AudioFormat baseFormat = in.getFormat();

            if (fmtOut != null && fmtOut.length > 0)
                fmtOut[0] = baseFormat;

            int channels = baseFormat.getChannels();
            int sampleSize = baseFormat.getSampleSizeInBits();

            byte[] pcmBytes = in.readAllBytes();
            int numFrames = pcmBytes.length / baseFormat.getFrameSize();

            float[][] pcm = new float[2][numFrames];
            ByteBuffer bb = ByteBuffer.wrap(pcmBytes).order(
                    baseFormat.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN
            );

            if (sampleSize != 16) {
                throw new UnsupportedOperationException("Only 16-bit PCM is supported");
            }

            for (int i = 0; i < numFrames; i++) {
                if (channels == 1) {
                    short s = bb.getShort();
                    float v = s / 32768f;
                    pcm[0][i] = v;
                    pcm[1][i] = v;
                } else { // stereo
                    short left = bb.getShort();
                    short right = bb.getShort();
                    pcm[0][i] = left / 32768f;
                    pcm[1][i] = right / 32768f;
                }
            }
            return pcm;
        }
    }

    public static void writeWav(float[][] pcm, AudioFormat fmt, File out) throws Exception {
        byte[] pcmBytes = toPCM16(pcm);

        try (ByteArrayInputStream bais = new ByteArrayInputStream(pcmBytes);
             AudioInputStream ais = new AudioInputStream(bais, fmt, pcm[0].length)) {

            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, out);
        }
    }

    public static byte[] toPCM16(float[][] pcm) {
        int samples = pcm[0].length;
        ByteBuffer bb = ByteBuffer.allocate(samples * 2 * 2).order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < samples; i++) {
            float vL = Math.max(-1f, Math.min(1f, pcm[0][i]));
            float vR = Math.max(-1f, Math.min(1f, pcm[1][i]));

            short L = (short) (vL * Short.MAX_VALUE);
            short R = (short) (vR * Short.MAX_VALUE);

            bb.putShort(L);
            bb.putShort(R);
        }
        return bb.array();
    }

    public static float[][] concatTrack(AudioTrack track) {
        List<float[][]> allSegments = new ArrayList<>();
        int totalSamples = 0;

        for (AudioComponent c : track.getChildren()) {
            if (c instanceof AudioSegment seg) {
                float[][] s = seg.getSamples();
                allSegments.add(s);
                totalSamples += s[0].length;
            }
        }

        float[][] out = new float[2][totalSamples];
        int pos = 0;

        for (float[][] s : allSegments) {
            int len = s[0].length;
            System.arraycopy(s[0], 0, out[0], pos, len);
            System.arraycopy(s[1], 0, out[1], pos, len);
            pos += len;
        }
        return out;
    }

    public static float[][] mixProject(AudioProject project) {
        List<float[][]> allTracks = new ArrayList<>();
        int maxSamples = 0;

        for (AudioComponent c : project.getChildren()) {
            if (c instanceof AudioTrack t) {
                float[][] trackPcm = concatTrack(t);
                allTracks.add(trackPcm);
                maxSamples = Math.max(maxSamples, trackPcm[0].length);
            }
        }

        float[][] mix = new float[2][maxSamples];

        for (float[][] trackPcm : allTracks) {
            int len = trackPcm[0].length;
            for (int i = 0; i < len; i++) {
                mix[0][i] += trackPcm[0][i];
                mix[1][i] += trackPcm[1][i];
            }
        }

        float max = 0f;
        for (int i = 0; i < maxSamples; i++) {
            max = Math.max(max, Math.abs(mix[0][i]));
            max = Math.max(max, Math.abs(mix[1][i]));
        }

        if (max > 1f) {
            float k = 1f / max;
            for (int i = 0; i < maxSamples; i++) {
                mix[0][i] *= k;
                mix[1][i] *= k;
            }
        }

        return mix;
    }

    public static void concatTrackToFile(AudioTrack track, File out, String fmt) throws Exception {
        float[][] pcm = concatTrack(track);
        AudioFormat format = track.getFormat();
        writeWav(pcm, format, out);
    }

    public static float[][] slice(float[][] source, int fromSample, int toSample) {
        if (source == null || source.length != 2 || fromSample >= toSample || toSample <= 0) {
            return new float[2][0];
        }

        int start = Math.max(0, fromSample);
        int end = Math.min(source[0].length, toSample);
        int len = end - start;
        if (len <= 0) return new float[2][0];

        float[][] slice = new float[2][len];
        System.arraycopy(source[0], start, slice[0], 0, len);
        System.arraycopy(source[1], start, slice[1], 0, len);
        return slice;
    }

    public static float[][] cut(float[][] source, int fromSample, int toSample) {
        if (source == null || source.length != 2) return new float[2][0];

        int totalLen = source[0].length;
        int start = Math.max(0, fromSample);
        int end = Math.min(totalLen, toSample);
        if (start >= end) return source;

        int leftLen = start;
        int rightLen = totalLen - end;
        int newLen = leftLen + rightLen;

        float[][] remaining = new float[2][newLen];

        if (leftLen > 0) {
            System.arraycopy(source[0], 0, remaining[0], 0, leftLen);
            System.arraycopy(source[1], 0, remaining[1], 0, leftLen);
        }
        if (rightLen > 0) {
            System.arraycopy(source[0], end, remaining[0], leftLen, rightLen);
            System.arraycopy(source[1], end, remaining[1], leftLen, rightLen);
        }
        return remaining;
    }

    public static float[][] splice(float[][] main, float[][] clip, int fromSample, int toSample) {
        if (clip == null || clip[0].length == 0) return main;
        if (main == null || main[0].length == 0) return clip;

        int mainLen = main[0].length;
        int clipLen = clip[0].length;

        int start = Math.max(0, fromSample);
        int end = Math.min(mainLen, toSample);
        if (end < start) end = start;

        int leftLen = start;
        int rightLen = mainLen - end;
        int newLen = leftLen + clipLen + rightLen;

        float[][] result = new float[2][newLen];

        if (leftLen > 0) {
            System.arraycopy(main[0], 0, result[0], 0, leftLen);
            System.arraycopy(main[1], 0, result[1], 0, leftLen);
        }

        System.arraycopy(clip[0], 0, result[0], leftLen, clipLen);
        System.arraycopy(clip[1], 0, result[1], leftLen, clipLen);

        if (rightLen > 0) {
            System.arraycopy(main[0], end, result[0], leftLen + clipLen, rightLen);
            System.arraycopy(main[1], end, result[1], leftLen + clipLen, rightLen);
        }

        return result;
    }

    public static float[][] reverse(float[][] samples, int fromSample, int toSample) {
        if (samples == null || samples.length != 2 || samples[0].length == 0) {
            return samples;
        }

        int totalSamples = samples[0].length;

        float[][] result = new float[2][totalSamples];
        System.arraycopy(samples[0], 0, result[0], 0, totalSamples);
        System.arraycopy(samples[1], 0, result[1], 0, totalSamples);

        int l = Math.max(0, fromSample);
        int r = Math.min(totalSamples - 1, toSample - 1);

        while (l < r) {
            float tempL = result[0][l];
            result[0][l] = result[0][r];
            result[0][r] = tempL;

            float tempR = result[1][l];
            result[1][l] = result[1][r];
            result[1][r] = tempR;

            l++;
            r--;
        }
        return result;
    }
}