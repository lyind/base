package net.talpidae.base.util;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;


public class BaseArguments
{
    @Getter
    private final List<String> arguments;

    public BaseArguments(String[] args)
    {
        arguments = Arrays.asList(args);
    }
}
