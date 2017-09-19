package net.talpidae.base.util.auth.scope;

import com.google.common.collect.Maps;
import com.google.inject.Key;

import java.util.Map;


public class AuthScope
{
    private final Map<Key<?>, Object> values = Maps.newHashMap();

    @SuppressWarnings("unchecked")
    public <T> T get(Key<T> key)
    {
        return (T) values.get(key);
    }


    public void put(Key<?> key, Object value)
    {
        values.put(key, value);
    }


    public boolean containsKey(Key<?> key)
    {
        return values.containsKey(key);
    }
}
