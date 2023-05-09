package de.fraunhofer.iais.eis.ids.jsonld;

public class SerializerFactory {

    private static final Serializer instance = new Serializer();

    public static Serializer getInstance() {
        return instance;
    }
}
