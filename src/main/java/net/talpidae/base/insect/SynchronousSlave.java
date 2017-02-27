package net.talpidae.base.insect;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.talpidae.base.insect.config.InsectSettings;
import net.talpidae.base.insect.config.QueenSettings;
import net.talpidae.base.insect.exchange.message.MappingPayload;

import javax.inject.Inject;


@Singleton
@Slf4j
public class SynchronousSlave extends Insect<InsectSettings> implements Slave
{
    @Inject
    public SynchronousSlave(QueenSettings settings)
    {
        super(settings, true);
    }


    @Override
    protected void postHandleMapping(MappingPayload mapping)
    {
        // TODO notify callers blocking for route endpoints
    }
}
