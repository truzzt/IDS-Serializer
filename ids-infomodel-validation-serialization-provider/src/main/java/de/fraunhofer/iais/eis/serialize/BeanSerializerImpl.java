package de.fraunhofer.iais.eis.serialize;

import de.fraunhofer.iais.eis.SerializationException;
import de.fraunhofer.iais.eis.ids.jsonld.Serializer;
import de.fraunhofer.iais.eis.ids.jsonld.SerializerFactory;
import de.fraunhofer.iais.eis.spi.BeanSerializer;

import java.io.IOException;

public class BeanSerializerImpl implements BeanSerializer {

    private final Serializer serializer = SerializerFactory.getInstance();

    @Override
    public String toRdf(Object obj) {
        try {
            return serializer.serialize(obj);
        }
        catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public <T> T fromRdf(String rdf, Class<T> valueType) {
        try {
            return serializer.deserialize(rdf, valueType);
        }
        catch (IOException e) {
            throw new SerializationException(e);
        }
    }

}
