package ar.cabal;

import org.jdom2.Element;
import org.jpos.core.ConfigurationException;
import org.jpos.iso.FilteredChannel;
import org.jpos.iso.ISOChannel;
import org.jpos.iso.ISOPackager;
import org.jpos.q2.QFactory;
import org.jpos.q2.iso.ChannelAdaptor;
import org.jpos.core.handlers.exception.ExceptionHandlerAware;

public class DynamicChannelAdaptor extends ChannelAdaptor {

    @Override
    public ISOChannel newChannel(Element e, QFactory f) throws ConfigurationException {
        String origin = QFactory.getAttributeValue(e, "origin");

        if (origin == null || origin.isBlank()) {
            throw new ConfigurationException("Missing 'origin' attribute in channel configuration.");
        }

        // Configuración dinámica según el origen
        ChannelConfig config = getChannelConfig(origin);

        // Crear canal
        ISOChannel channel = (ISOChannel) f.newInstance(config.channelClass);
        if (channel == null) {
            throw new ConfigurationException("Unable to instantiate ISOChannel for origin: " + origin);
        }

        // Asignar packager
        ISOPackager packager = (ISOPackager) f.newInstance(config.packagerClass);
        if (packager == null) {
            throw new ConfigurationException("Unable to instantiate ISOPackager for origin: " + origin);
        }
        channel.setPackager(packager);

        // Configurar logger y propiedades
        f.setLogger(channel, e);
        f.setConfiguration(channel, e, config.packagerConfig, config.port);
        f.setConfiguration(packager, e, config.packagerConfig, config.port);

        // Configurar header si está definido
        String header = QFactory.getAttributeValue(e, "header");
        if (header != null && !header.isBlank()) {
            QFactory.invoke(channel, "setHeader", header);
        }

        // Aplicar filtros y handlers si corresponde
        if (channel instanceof FilteredChannel filteredChannel) {
            addFilters(filteredChannel, e, f);
        }

        if (channel instanceof ExceptionHandlerAware handlerAware) {
            addExceptionHandlers(handlerAware, e, f);
        }

        // Asignar nombre del canal si existe
        if (getName() != null) {
            channel.setName(getName());
        }

        return channel;
    }

    /**
     * Seteamos configuracion de canal y packager segun origen
     */
    private ChannelConfig getChannelConfig(String origin) throws ConfigurationException {
        return switch (origin.toUpperCase()) {
            case "VISA" -> new ChannelConfig(
                    "org.jpos.iso.channel.B24Channel",
                    "org.jpos.iso.packager.GenericPackager",
                    "cfg/iso93ascii.xml",
                    "11350"
            );
            case "POSNET" -> new ChannelConfig(
                    "org.jpos.iso.channel.B24Channel",
                    "org.jpos.iso.packager.GenericPackager",
                    "cfg/iso87ascii_posnet_dobleBitmap.xml",
                    "11750"
            );
            default -> throw new ConfigurationException("Unsupported origin: " + origin);
        };
    }


    private static class ChannelConfig {
        final String channelClass;
        final String packagerClass;
        final String packagerConfig;
        final String port;

        ChannelConfig(String channelClass, String packagerClass, String packagerConfig, String port) {
            this.channelClass = channelClass;
            this.packagerClass = packagerClass;
            this.packagerConfig = packagerConfig;
            this.port = port;
        }
    }
}
