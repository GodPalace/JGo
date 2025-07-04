package com.godpalace.jgo.go.embed;

import lombok.Getter;

import java.io.*;
import java.net.URL;

public final class EmbedFile {
    @Getter
    private final URL url;

    public EmbedFile(String url, Class<?> parent) {
        try {
            this.url = parent.getClassLoader().getResource(url);
        } catch (Exception e) {
            throw new ResourceNotFoundException(e.getMessage());
        }
    }

    public void releaseToFile(String file) {
        releaseToFile(new File(file));
    }

    public void releaseToFile(File file) {
        try (FileOutputStream out = new FileOutputStream(file)) {
            releaseToStream(out);
        } catch (IOException e) {
            throw new ReleaseException(e.getMessage());
        }
    }

    public void releaseToStream(OutputStream stream) {
        try (InputStream in = url.openStream()) {
            if (in == null) {
                throw new ResourceNotFoundException("Resource not found: " + url);
            }

            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) != -1) {
                stream.write(buffer, 0, len);
            }
        } catch (IOException e) {
            throw new ReleaseException(e.getMessage());
        }
    }

    public byte[] readBytes() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            releaseToStream(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ReleaseException(e.getMessage());
        }
    }

    public InputStream openStream() {
        try {
            return url.openStream();
        } catch (IOException e) {
            throw new ReleaseException(e.getMessage());
        }
    }

    public long size() {
        try {
            return url.openConnection().getContentLengthLong();
        } catch (IOException e) {
            throw new ReleaseException(e.getMessage());
        }
    }
}
