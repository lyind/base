package net.talpidae.base.util.file;

import lombok.val;

import java.text.Normalizer;


public final class FileName
{
    public static String sanitize(String name)
    {
        val sanitizedName = Normalizer.normalize(name, Normalizer.Form.NFKD) // unicode normalization
                .replace('\\', '/') // path separator
                .replaceAll("[^a-zA-Z0-9-_.\\s]", "") // restrict to some ASCII chars
                .replaceAll("^[\\s.-]+", "") // trim '.', '-' and whitespace from the beginning
                .replaceAll("[\\s.-]+$", "") // also trim from the end
                .replaceAll("\\s+", "-"); // replace inner whitespaces with '-'

        return sanitizedName.substring(0, Math.min(255, sanitizedName.length()));
    }


    private FileName()
    {

    }
}
