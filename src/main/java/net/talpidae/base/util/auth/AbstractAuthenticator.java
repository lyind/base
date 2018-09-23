/*
 * Copyright (C) 2017  Jonas Zeiger <jonas.zeiger@talpidae.net>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.talpidae.base.util.auth;

import java.sql.Date;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;


@Slf4j
public abstract class AbstractAuthenticator implements Authenticator
{
    public static final SignatureAlgorithm SIGNATURE_ALGORITHM = SignatureAlgorithm.HS256;

    private final AtomicReference<String[]> keysRef = new AtomicReference<>(new String[0]);

    private final AtomicReference<JwtParser[]> parsersRef = new AtomicReference<>(new JwtParser[0]);


    public AbstractAuthenticator(String[] keys)
    {
        setKeys(keys);
    }


    /**
     * Create a new token using the first secret key in the array for signing.
     */
    @Override
    public String createToken(@NonNull String subject)
    {
        val now = Instant.now();

        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(getExpirationTime())))
                .signWith(SIGNATURE_ALGORITHM, keysRef.get()[0])
                .compact();
    }

    @Override
    public Claims evaluateToken(@NonNull String token)
    {
        val parserCopy = this.parsersRef.get();
        for (JwtParser parser : parserCopy)
        {
            try
            {
                return parser.parseClaimsJws(token).getBody();
            }
            catch (JwtException e)
            {
                // ignore, we just try the next parser/key
            }
        }

        // can't validate token
        return null;
    }

    @Override
    public String[] getKeys()
    {
        val keys = keysRef.get();

        return Arrays.copyOf(keys, keys.length);
    }

    protected void setKeys(String[] keys)
    {
        val parserList = new ArrayList<JwtParser>();
        for (val key : keys)
        {
            parserList.add(Jwts.parser().setSigningKey(key));
        }

        // first, publish ability to parse with the new keys
        this.parsersRef.set(parserList.toArray(new JwtParser[parserList.size()]));

        // second, allow signing with the new keys
        this.keysRef.set(Arrays.copyOf(keys, keys.length));
    }

    protected abstract TemporalAmount getExpirationTime();
}
