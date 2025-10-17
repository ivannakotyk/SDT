package com.ivanka.audioeditor.server.service.adapter;

import java.io.File;

public interface IAudioFormatAdapter {
    File convert(File inputFile, String targetFormat) throws Exception;
}
