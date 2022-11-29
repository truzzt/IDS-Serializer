package de.fraunhofer.iais.eis.ids.jsonld;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeName;
import de.fraunhofer.iais.eis.util.RdfResource;
import de.fraunhofer.iais.eis.util.TypedLiteral;
import org.apache.jena.datatypes.DatatypeFormatException;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RiotException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;


/**
 * Internal class to handle the parsing of JSON-LD into java objects
 * @author mboeckmann
 */
class Parser {

    Logger logger = LoggerFactory.getLogger(Parser.class);
    final String IDS_DEFAULT_ENUM_PREFIX = "de.fraunhofer.iais.eis.Default";
    static Map<String, String> knownNamespaces = new HashMap<>();

    /**
     * Main internal method for creating a java object from a given RDF graph and a URI of the object to handle
     * @param inputModel Model on which queries are to be evaluated from which information can be retrieved
     * @param objectUri URI of the object to be handled
     * @param targetClass Variable containing the class which should be returned
     * @param <T> Class which should be returned
     * @return Object of desired class, filled with the values extracted from inputModel
     * @throws IOException thrown if the parsing fails
     */
    private <T> T handleObject(Model inputModel, String objectUri, Class<T> targetClass) throws IOException {
        try {

            //if(!targetClass.getSimpleName().endsWith("Impl")) //This would not work for "TypedLiteral", "RdfResource" and so on
            //Check whether we are dealing with an instantiable class (i.e. no interface and no abstract class)
            if (targetClass.isInterface() || Modifier.isAbstract(targetClass.getModifiers())) {
                //We don't know the desired class yet (current targetClass is not instantiable). This is only known for the root object
                ArrayList<Class<?>> implementingClasses = getImplementingClasses(targetClass);

                //Get a list of all "rdf:type" statements in our model
                String queryString = "SELECT ?type { BIND(<" + objectUri + "> AS ?s). ?s a ?type . }";
                Query query = QueryFactory.create(queryString);
                QueryExecution queryExecution = QueryExecutionFactory.create(query, inputModel);
                ResultSet resultSet = queryExecution.execSelect();

                if (!resultSet.hasNext()) {
                    queryExecution.close();
                    throw new IOException("Could not extract class of child object. ID: " + objectUri);
                }

                //Class<?> candidateClass = null;

                String fullName = "No triple present indicating type.";
                while (resultSet.hasNext()) {
                    QuerySolution solution = resultSet.nextSolution();
                    fullName = solution.get("type").toString();

                    //Expected URI is something like https://w3id.org/idsa/core/ClassName (and we want ClassName)
                    String className = fullName.substring(fullName.lastIndexOf('/') + 1);

                    //Some namespaces use "#" instead of "/"
                    if (className.contains("#")) {
                        className = className.substring(className.lastIndexOf("#") + 1);
                    }

                    for (Class<?> currentClass : implementingClasses) {
                        //Is this class instantiable?
                        if (!currentClass.isInterface() && !Modifier.isAbstract(currentClass.getModifiers())) {
                            //candidateClass = currentClass;
                            if (currentClass.getSimpleName().equals(className) || currentClass.getSimpleName().equals(Serializer.implementingClassesNamePrefix + className + Serializer.implementingClassesNameSuffix)) {
                                targetClass = (Class<T>) currentClass;
                                break;
                            }
                        }
                    }
                }
                queryExecution.close();
                //Did we find "the" class, i.e. instantiable and name matches?
                if (targetClass.isInterface() || Modifier.isAbstract(targetClass.getModifiers())) {
                    //No, the current targetClass cannot be instantiated. Do we have a candidate class?
                    //if (candidateClass != null) {
                        throw new IOException("Did not find an instantiable class for " + objectUri + " matching expected class name (" + targetClass.getSimpleName() + "). Object has type: " + fullName);
                        //targetClass = (Class<T>) candidateClass;
                    //}
                }
            }

            //Enums have no constructors
            if(targetClass.isEnum())
            {
                return handleEnum(targetClass, objectUri);
            }

            //Get constructor (which is package private for our classes) and make it accessible
            Constructor<T> constructor = targetClass.getDeclaredConstructor();
            constructor.setAccessible(true);

            //Instantiate new object, which will be returned at the end
            T returnObject = constructor.newInstance();

            //Get methods
            Method[] methods = returnObject.getClass().getDeclaredMethods();

            //Store methods in map. Key is the name of the RDF property without ids prefix
            //Use a TreeMap to have the properties sorted alphabetically
            Map<String, Method> methodMap = new TreeMap<>();//(Comparator.reverseOrder());


            //Get all relevant methods (setters, but not for label, comment or external properties)
            Arrays.stream(methods).filter(method -> {
                String name = method.getName();
                //Filter out irrelevant methods
                return name.startsWith("set") && !name.equals("setProperty") && !name.equals("setComment") && !name.equals("setLabel") && !name.equals("setId");
            }).forEach(method -> {
                //Remove "set" part
                String reducedName = method.getName().substring(3);

                //Turn first character to lower case
                char[] c = reducedName.toCharArray();
                c[0] = Character.toLowerCase(c[0]);
                String finalName = new String(c);
                methodMap.put(finalName, method);

            });

            //There is no "setId" method in our CodeGen generated classes, so we get the field
            Field idField = returnObject.getClass().getDeclaredField("id");

            //Store whether or not it was accessible, so that we can undo making it accessible
            boolean wasAccessible = idField.isAccessible();
            idField.setAccessible(true);

            //Set the ID of the object to be identical with the objectUri parameter
            idField.set(returnObject, new URI(objectUri));
            idField.setAccessible(wasAccessible);

            //Is this a trivial class with 0 fields? If so, the generated query would be "SELECT { }", which is illegal
            if(methodMap.isEmpty())
            {
                return returnObject;
            }

            //Query for all known (i.e. defined in the underlying class) properties and their values
            String queryString = createKnownPropertiesQueryString(objectUri, targetClass, methodMap);

            //Evaluate query for known properties
            Query query = QueryFactory.create(queryString);
            QueryExecution queryExecution = QueryExecutionFactory.create(query, inputModel);
            ResultSet resultSet = queryExecution.execSelect();

            if (!resultSet.hasNext()) {
                queryExecution.close();
                //no content... ONLY allowed, if the class has optional fields only (i.e. no mandatory fields)!
                if (checkIfNotNullAnnotatedFieldExists(methodMap, targetClass)) {
                    //There is at least one mandatory field. Hence, incoming message was illegal. Preparing some error message to be returned

                    //Create a query to find out which element is missing
                    StringBuilder diagnosticString = new StringBuilder();
                    diagnosticString.append("PREFIX ids: <https://w3id.org/idsa/core/>\n")
                            .append("PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n");
                    for(Map.Entry<String, String> entry : knownNamespaces.entrySet())
                    {
                        diagnosticString.append("PREFIX ").append(entry.getKey());
                        if(!entry.getKey().endsWith(":")) {
                            diagnosticString.append(":");
                        }
                        diagnosticString.append(" <").append(entry.getValue()).append(">\n");
                    }
                    diagnosticString.append("SELECT ?o { <").append(objectUri).append("> ");

                    List<String> missingElements = new ArrayList<>();

                    StringBuilder notNullableFieldNames = new StringBuilder();
                    for (Map.Entry<String, Method> entry : methodMap.entrySet()) {
                        Field field = getFieldByName(targetClass, entry.getKey());
                        if (field.isAnnotationPresent(NotNull.class)) {
                            if (notNullableFieldNames.length() > 0) {
                                notNullableFieldNames.append(", ");
                            }
                            notNullableFieldNames.append(entry.getKey());

                            //Get the name of the property as written in RDF, i.e. "prefix:propertyName"
                            Optional<String> currentAnnotation = Arrays.stream(field.getAnnotation(JsonAlias.class).value()).map(this::ignoreSuffix).filter(annotation -> annotation.contains(":")).findFirst();
                            if(currentAnnotation.isPresent()) {
                                //Query for this field (we know already that it is mandatory)
                                String checkIfMandatoryFieldPresent = diagnosticString + currentAnnotation.get() + " ?o }";
                                Query checkExistsQuery = QueryFactory.create(checkIfMandatoryFieldPresent);

                                QueryExecution checkExistsQueryExecution = QueryExecutionFactory.create(checkExistsQuery, inputModel);
                                ResultSet checkExistsResultSet = checkExistsQueryExecution.execSelect();
                                //Check if we can find some value. If not, this field is definitely causing errors
                                if(!checkExistsResultSet.hasNext())
                                {
                                    missingElements.add(currentAnnotation.get());
                                }
                            }
                        }
                    }
                    logger.info("Executed query: " + queryString);
                    if(missingElements.size() > 0)
                    {
                        StringBuilder errorMessage = new StringBuilder("The following mandatory field(s) of " + returnObject.getClass().getSimpleName().replace("Impl", "") + " are not filled or invalid: ");
                        for(int i = 0; i < missingElements.size(); i++)
                        {
                            if(i == 0)
                            {
                                errorMessage.append(missingElements.get(i));
                            }
                            else
                            {
                                errorMessage.append(", ").append(missingElements.get(i));
                            }
                        }
                        throw new IOException(errorMessage + ". Note that the value of \"@id\" fields MUST be a valid URI (e.g. emails preceded by \"mailto:\"). Mandatory fields are: " + notNullableFieldNames);
                    }
                    throw new IOException("Mandatory field of " + returnObject.getClass().getSimpleName().replace("Impl", "") + " not filled or invalid. Note that the value of \"@id\" fields MUST be a valid URI (e.g. emails preceded by \"mailto:\"). Mandatory fields are: " + notNullableFieldNames);
                }

                return returnObject;
            }


            // now as all declared instances and classes are treated, which are also represented in the respective java
            // dependency, take care about the ones within foreign namespaces and add those to the 'properties' field
            // note that not all models (e.g. AAS) have such methods. In case they do not exist, skip adding external properties

            try {
                //Now that we searched for all "known properties", let's search for all unrecognized content and append it to a generic properties map
                String queryForOtherProperties = createUnknownPropertiesQueryString(objectUri, targetClass, methodMap);
                Query externalPropertiesQuery = QueryFactory.create(queryForOtherProperties);
                QueryExecution externalPropertiesQueryExecution = QueryExecutionFactory.create(externalPropertiesQuery, inputModel);
                ResultSet externalPropertiesResultSet = externalPropertiesQueryExecution.execSelect();

                Method setProperty = returnObject.getClass().getDeclaredMethod("setProperty", String.class, Object.class);
                Method getProperties = returnObject.getClass().getDeclaredMethod("getProperties");

                while (externalPropertiesResultSet.hasNext()) {
                    QuerySolution externalPropertySolution = externalPropertiesResultSet.next();

                    HashMap<String, Object> currentProperties = (HashMap<String, Object>) getProperties.invoke(returnObject);

                    //Avoid NullPointerException
                    if (currentProperties == null) {
                        currentProperties = new HashMap<>();
                    }

                    String propertyUri = externalPropertySolution.get("p").toString();

                    //Does this key already exist? If yes, we need to store the value as array to not override them
                    if (currentProperties.containsKey(propertyUri)) {
                        //If it is not an array list yet, turn it into one
                        if (!(currentProperties.get(propertyUri) instanceof ArrayList)) {
                            ArrayList<Object> newList = new ArrayList<>();
                            newList.add(currentProperties.get(propertyUri));
                            currentProperties.put(propertyUri, newList);
                        }
                    }

                    //Literals and complex objects need to be handled differently
                    //Literals can be treated as flat values, whereas complex objects require recursive calls
                    if (externalPropertySolution.get("o").isLiteral()) {
                        Object o = handleForeignLiteral(externalPropertySolution.getLiteral("o"));
                        //If it is already an ArrayList, add new value to it
                        if (currentProperties.containsKey(propertyUri)) {
                            ArrayList<Object> currentPropertyArray = ((ArrayList<Object>) currentProperties.get(propertyUri));
                            currentPropertyArray.add(o);
                            setProperty.invoke(returnObject, propertyUri, currentPropertyArray);
                        }
                        //Otherwise save as new plain value
                        else {
                            setProperty.invoke(returnObject, propertyUri, o);
                        }
                    } else {
                        //It is a complex object. Distinguish whether or not we need to store as array
                        HashMap<String, Object> subMap = handleForeignNode(externalPropertySolution.getResource("o"), new HashMap<>(), inputModel);
                        subMap.put("@id", externalPropertySolution.getResource("o").getURI());
                        if (currentProperties.containsKey(propertyUri)) {
                            ArrayList<Object> currentPropertyArray = ((ArrayList<Object>) currentProperties.get(propertyUri));
                            currentPropertyArray.add(subMap);
                            setProperty.invoke(returnObject, propertyUri, currentPropertyArray);
                        } else {
                            setProperty.invoke(returnObject, propertyUri, subMap);
                        }
                    }
                }
                externalPropertiesQueryExecution.close();
            }
            catch (NoSuchMethodException ignored)
            {
                //Method does not exist, skip
            }

            //SPARQL binding present, iterate over result and construct return object
            while (resultSet.hasNext()) {
                QuerySolution querySolution = resultSet.next();

                if (resultSet.hasNext()) {
                    String value1 = "", value2 = "", parameterName = "";
                    QuerySolution querySolution2 = resultSet.next();
                    Iterator<String> varNamesIt = querySolution2.varNames();
                    while(varNamesIt.hasNext())
                    {
                        String varName = varNamesIt.next();
                        if(querySolution.contains(varName))
                        {
                            if(!querySolution.get(varName).equals(querySolution2.get(varName)))
                            {
                                parameterName = varName;
                                value1 = querySolution.get(varName).toString();
                                value2 = querySolution2.get(varName).toString();
                                break;
                            }
                        }
                    }
                    if(!value1.isEmpty())
                    {
                        throw new IOException(objectUri + " has multiple values for " + parameterName + ", which is not allowed. Values are: " + value1 + " and " + value2);
                    }
                    throw new IOException("Multiple bindings for SPARQL query which should only have one binding. Input contains multiple values for a field which may occur only once.");
                }

                for (Map.Entry<String, Method> entry : methodMap.entrySet()) {

                    //What is this method setting? Get the expected parameter type and check whether it is some complex sub-object and whether this is a list
                    Class<?> currentType = entry.getValue().getParameterTypes()[0];
                    //Is this a field which is annotated by NOT NULL?
                    //boolean nullable = !targetClass.getDeclaredField("_" + entry.getKey()).isAnnotationPresent(NotNull.class);

                    String sparqlParameterName = entry.getKey();

                    if (Collection.class.isAssignableFrom(currentType)) {
                        sparqlParameterName += "s"; //plural form for the concatenated values
                    }
                    if (querySolution.contains(sparqlParameterName)) {
                        String currentSparqlBinding = querySolution.get(sparqlParameterName).toString();

                        if (currentType.isEnum()) {
                            entry.getValue().invoke(returnObject, handleEnum(currentType, currentSparqlBinding));
                            continue;
                        }

                        if (checkIfClassIsDefaultInterfaceInstance(currentType.getName(), inputModel, currentSparqlBinding)) {
                            Object instance = getDefaultInterfaceInstance(currentType.getName(), currentSparqlBinding);
                            entry.getValue().invoke(returnObject, instance);
                            continue;
                        }

                        //There is a binding. If it is a complex sub-object, we need to recursively call this function
                        if (Collection.class.isAssignableFrom(currentType)) {
                            //We are working with ArrayLists.
                            //Here, we need to work with the GenericParameterTypes instead to find out what kind of ArrayList we are dealing with
                            String typeName = extractTypeNameFromList(entry.getValue().getGenericParameterTypes()[0]);
                            if (isArrayListTypePrimitive(entry.getValue().getGenericParameterTypes()[0])) {
                                if (typeName.endsWith("TypedLiteral")) {
                                    try {
                                        currentSparqlBinding = querySolution.get(sparqlParameterName + "Lang").toString();
                                    } catch (NullPointerException e) {
                                        logger.warn("Failed to retrieve localized/typed values of " + currentSparqlBinding + ". Make sure that namespaces used in this property are known and valid. Proceeding without localized values and interpreting as string.");
                                    }
                                }
                                ArrayList<Object> list = new ArrayList<>();
                                //Two pipes were used as delimiter above
                                //Introduce set to deduplicate
                                Set<String> allElements = new HashSet<>(Arrays.asList(currentSparqlBinding.split("\\|\\|")));
                                for (String s : allElements) {
                                    Literal literal;
                                    //querySolution.get(sparqlParameterName).
                                    if (s.endsWith("@")) {
                                        s = s.substring(2, s.length() - 3);
                                        literal = ResourceFactory.createStringLiteral(s);
                                    } else if (s.startsWith("\\")) {
                                        //turn something like \"my Desc 1\"@en to "my Desc 1"@en
                                        s = s.substring(1).replace("\\\"@", "\"@");
                                        literal = ResourceFactory.createLangLiteral(s.substring(1, s.lastIndexOf("@") - 1), s.substring(s.lastIndexOf("@") + 1));
                                    } else {
                                        literal = ResourceFactory.createPlainLiteral(s);
                                    }

                                    //Is the type of the ArrayList some built in Java primitive?

                                    if (builtInMap.containsKey(typeName)) {
                                        //Yes, it is. We MUST NOT call Class.forName(name)!
                                        list.add(handlePrimitive(builtInMap.get(typeName), literal, null));
                                    } else {
                                        //Not a Java primitive, we may call Class.forName(name)
                                        list.add(handlePrimitive(Class.forName(typeName), literal, s));
                                    }
                                }

                                entry.getValue().invoke(returnObject, list);

                            } else {
                                //List of complex sub-objects, such as a list of Resources in a ResourceCatalog
                                ArrayList<Object> list = new ArrayList<>();
                                Set<String> allElements = new HashSet<>(Arrays.asList(currentSparqlBinding.split("\\|\\|")));
                                for (String s : allElements) {
                                    if (Class.forName(typeName).isEnum()) {
                                        list.add(handleEnum(Class.forName(typeName), s));
                                    } else {
                                        if (checkIfClassIsDefaultInterfaceInstance(typeName, inputModel, s)) {
                                            Object instance = getDefaultInterfaceInstance(typeName, s);
                                            list.add(instance);
                                        } else {
                                            if (!sparqlParameterName.endsWith("AsUris")) {
                                                //Special case: For parameters like "assignee" there is the possibility that only a URI for "assigneeAsUri"
                                                //is given. In this case there is no @type, but we can ignore that because we only need to parse the URI.
                                                try {
                                                    list.add(handleObject(inputModel, s, Class.forName(typeName)));
                                                } catch (IOException exception) {
                                                    if (!exception.getMessage().equals(("Could not extract class of child object. ID: " + s))) {
                                                        throw new IOException(exception.getMessage());
                                                    }
                                                }
                                            } else {
                                                //Standard case
                                                list.add(handleObject(inputModel, s, Class.forName(typeName)));
                                            }
                                        }
                                    }
                                }
                                if (!sparqlParameterName.endsWith("AsUris")) {
                                    //Special case: For parameters like "assignee" don't invoke the setter if the list is empty
                                    //because this will set the additional property, e.g. "assigneeAsUri", to the empty list.
                                    //As the "...AsUri" property
                                    //is processed before the "regular" version, this could lead to information loss.
                                    if (!list.isEmpty()) {
                                        entry.getValue().invoke(returnObject, list);
                                    }
                                } else {
                                    entry.getValue().invoke(returnObject, list);
                                }
                            }
                        }

                        //Not an ArrayList of objects expected, but rather one object
                        else {
                            //Our implementation of checking for primitives (i.e. also includes URLs, Strings, XMLGregorianCalendars, ...)
                            if (isPrimitive(currentType)) {

                                Literal literal = null;
                                try {
                                    literal = querySolution.getLiteral(sparqlParameterName);
                                } catch (Exception ignored) {
                                }
                                if (sparqlParameterName.endsWith("AsUri")) {
                                    try {
                                        Class<?> clazz = methodMap.get(sparqlParameterName.substring(0, sparqlParameterName.length() - 5) + "AsObject").getParameterTypes()[0];
                                        if (!clazz.isEnum()) {
                                            Object o = handleObject(inputModel, currentSparqlBinding, clazz);
                                        }
                                    } catch (IOException exception) {
                                        if (exception.getMessage().equals(("Could not extract class of child object. ID: " + currentSparqlBinding))){
                                            entry.getValue().invoke(returnObject, handlePrimitive(currentType, literal, currentSparqlBinding));
                                        }
                                    }
                                } else {
                                    entry.getValue().invoke(returnObject, handlePrimitive(currentType, literal, currentSparqlBinding));
                                }
                            } else {
                                //Not a primitive object, but a complex sub-object. Recursively call this function to handle it
                                if (!sparqlParameterName.endsWith("AsUri")){
                                    //Special case: For parameters like "assignee" there is the possibility that only a URI for "assigneeAsUri"
                                    //is given. In this case there is no @type, but we can ignore that because we only need to parse the URI.
                                    try {
                                        entry.getValue().invoke(returnObject, handleObject(inputModel, currentSparqlBinding, entry.getValue().getParameterTypes()[0]));
                                    } catch (IOException exception) {
                                        if (!exception.getMessage().equals(("Could not extract class of child object. ID: " + currentSparqlBinding))){
                                            throw new IOException(exception.getMessage());
                                        }
                                    }
                                } else {
                                    //Standard case
                                    entry.getValue().invoke(returnObject, handleObject(inputModel, currentSparqlBinding, entry.getValue().getParameterTypes()[0]));
                                }
                            }
                        }
                    }

                }
            }
            queryExecution.close();

            return returnObject;
        } catch (NoSuchMethodException | NullPointerException | IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchFieldException | URISyntaxException | DatatypeConfigurationException | ClassNotFoundException e) {
            throw new IOException("Failed to instantiate desired class (" + targetClass.getName() + ")", e);
        }
    }

    private <T> String createKnownPropertiesQueryString(String objectUri, Class<T> targetClass, Map<String, Method> methodMap) throws NoSuchFieldException {
        List<String> groupByKeys = new ArrayList<>();
        StringBuilder queryStringBuilder = new StringBuilder();

        addPrefixesToQueryBuilder(queryStringBuilder);
        addSelectToQueryBuilder(methodMap, groupByKeys, queryStringBuilder);
        addWhereToQueryBuilder(objectUri, targetClass, methodMap, queryStringBuilder);
        addGroupByToQueryBuilder(groupByKeys, queryStringBuilder);

        return queryStringBuilder.toString();
    }

    private <T> String createUnknownPropertiesQueryString(String objectUri, Class<T> targetClass, Map<String, Method> methodMap) throws NoSuchFieldException {
        //SELECT { ?s ?p ?o } { ?s ?p ?o. FILTER(?p NOT IN (liste der ids: properties)) }
        StringBuilder queryForOtherProperties = new StringBuilder();

        addPrefixesToQueryBuilder(queryForOtherProperties);
        addSelectForOtherPropertiesToQueryBuilder(queryForOtherProperties);
        addWhereForOtherPropertiesToQueryBuilder(objectUri, targetClass, methodMap, queryForOtherProperties);

        return queryForOtherProperties.toString();
    }

    private void addPrefixesToQueryBuilder(StringBuilder queryStringBuilder) {
        queryStringBuilder.append("PREFIX ids: <https://w3id.org/idsa/core/>\n")
                .append("PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n");
        for(Map.Entry<String, String> entry : knownNamespaces.entrySet()) {
            addKnownNamespaceEntryToQueryBuilder(queryStringBuilder, entry);
        }
    }

    private void addKnownNamespaceEntryToQueryBuilder(StringBuilder queryStringBuilder, Map.Entry<String, String> entry){
        queryStringBuilder.append("PREFIX ").append(entry.getKey());
        if(!entry.getKey().endsWith(":")) {
            queryStringBuilder.append(":");
        }
        queryStringBuilder.append(" <").append(entry.getValue()).append(">\n");
    }

    private void addSelectToQueryBuilder(Map<String, Method> methodMap, List<String> groupByKeys, StringBuilder queryStringBuilder) {
        queryStringBuilder.append("SELECT");
        methodMap.forEach((key, value) -> {
            //Is the return type some sort of List?
            if (Collection.class.isAssignableFrom(value.getParameterTypes()[0])) {
                addSelectListEntryToQueryBuilder(key, value, groupByKeys, queryStringBuilder);
            } else {
                addSelectSingleEntryToQueryBuilder(key, groupByKeys, queryStringBuilder);
            }
        });
    }

    private void addSelectListEntryToQueryBuilder(String key, Method value, List<String> groupByKeys, StringBuilder queryStringBuilder) {
        //Concatenate multiple values together using some delimiter
        String typeName = getTypeNameOrNullFromMethod(value);
        if (typeName != null && typeName.endsWith("TypedLiteral")) {
            //For TypedLiterals, add the option to capture the lang string versions
            queryStringBuilder.append("?").append(key).append("sLang ");
            addToGroupByKeys(groupByKeys, key + "sLang");
        }
        queryStringBuilder.append(" ?").append(key).append("s ");
        addToGroupByKeys(groupByKeys, key + "s");
    }

    private void addSelectSingleEntryToQueryBuilder(String key, List<String> groupByKeys, StringBuilder queryStringBuilder) {
        queryStringBuilder.append(" ?").append(key);
        addToGroupByKeys(groupByKeys, key);
    }

    private <T> void addWhereToQueryBuilder(String objectUri, Class<T> targetClass, Map<String, Method> methodMap, StringBuilder queryStringBuilder) throws NoSuchFieldException {
        //Start WHERE part
        queryStringBuilder.append(" WHERE { ");

        //Make sure that the object is of the correct type
        //This is particularly relevant in case of all fields being optional -- then one could simply parse a random object
        addWhereRdfTypeStatementToQueryBuilder(objectUri, targetClass, queryStringBuilder);

        //Add one subquery per property
        addSelectSubqueryToQueryBuilder(objectUri, queryStringBuilder, methodMap, targetClass);

        //End WHERE part
        queryStringBuilder.append(" } ");
    }

    private <T> void addWhereRdfTypeStatementToQueryBuilder(String objectUri, Class<T> targetClass, StringBuilder queryStringBuilder) {
        queryStringBuilder.append(" <").append(objectUri).append("> a ").append(wrapIfUri(targetClass.getAnnotation(JsonTypeName.class).value())).append(". ");
    }

    private <T> void addSubQueryWhereToQueryBuilder(String objectUri, StringBuilder queryStringBuilder, String key, Class<T> targetClass) throws NoSuchFieldException {
        Field field = getFieldByName(targetClass, key);
        boolean nullable = !field.isAnnotationPresent(NotNull.class);

        queryStringBuilder.append(" WHERE { ");
        addOptionalStartToQueryBuilderIfNullable(queryStringBuilder, nullable);

        //Add statement: subject-predicate-object
        addSubQuerySubjectToQueryBuilder(objectUri, queryStringBuilder);
        addSubQueryPropertyToQueryBuilder(queryStringBuilder, key, field);
        addSubQueryObjectToQueryBuilder(queryStringBuilder, key);

        addOptionalEndToQueryBuilderIfNullable(queryStringBuilder, nullable);
        queryStringBuilder.append("}}");
    }

    private void addSubQuerySubjectToQueryBuilder(String objectUri, StringBuilder queryStringBuilder) {
        queryStringBuilder.append(" <").append(objectUri).append("> ");
    }

    private void addSubQueryPropertyToQueryBuilder(StringBuilder queryStringBuilder, String key, Field field) {
        if(field.getAnnotation(JsonAlias.class) != null) {
            Optional<String> currentAnnotation = Arrays.stream(field.getAnnotation(JsonAlias.class).value()).map(this::ignoreSuffix).map(this::wrapIfUri).filter(annotation -> annotation.contains(":")).findFirst();
            currentAnnotation.ifPresent(queryStringBuilder::append);
        } else {
            logger.warn("Failed to retrieve JsonAlias for field " + field + ". Assuming ids:" + key);
            queryStringBuilder.append("ids:").append(key);
        }
    }

    private void  addSubQueryObjectToQueryBuilder(StringBuilder queryStringBuilder, String key) {
        queryStringBuilder.append(" ?").append(key).append(" .");
    }

    private void addOptionalStartToQueryBuilderIfNullable(StringBuilder queryStringBuilder, boolean nullable) {
        if (nullable) {
            queryStringBuilder.append(" OPTIONAL { ");
        }
    }

    private void addOptionalEndToQueryBuilderIfNullable(StringBuilder queryStringBuilder, boolean nullable) {
        if (nullable) {
            queryStringBuilder.append("} ");
        }
    }

    private <T> void addSelectSubqueryToQueryBuilder(String objectUri, StringBuilder queryStringBuilder, Map<String, Method> methodMap, Class<T> targetClass) throws NoSuchFieldException {
        for (Map.Entry<String, Method> entry : methodMap.entrySet()) {
            queryStringBuilder.append(" { SELECT");
            //Is the return type some sort of List?
            if (Collection.class.isAssignableFrom(entry.getValue().getParameterTypes()[0])) {
                addSelectSubqueryListEntryToQueryBuilder(entry.getKey(), entry.getValue(), queryStringBuilder);
            } else {
                addSelectSingleEntryToQueryBuilder(entry.getKey(), null, queryStringBuilder);
            }
            addSubQueryWhereToQueryBuilder(objectUri, queryStringBuilder, entry.getKey(), targetClass);
        }
    }

    private void addSelectSubqueryListEntryToQueryBuilder(String key, Method value, StringBuilder queryStringBuilder) {
        //Concatenate multiple values together using some delimiter
        String typeName = getTypeNameOrNullFromMethod(value);
        if (typeName != null && typeName.endsWith("TypedLiteral")) {
            //For TypedLiterals, add the option to capture the lang string versions
            queryStringBuilder.append(" (GROUP_CONCAT(CONCAT('\"',?")
                    .append(key).append(",'\"@', lang(?")
                    .append(key).append("));separator=\"||\") AS ?")
                    .append(key).append("sLang) ");
        }
        queryStringBuilder.append(" (GROUP_CONCAT(?").append(key).append(";separator=\"||\") AS ?").append(key).append("s) ");
    }

    private void addToGroupByKeys(List<String> groupByKeys, String key) {
        if (groupByKeys != null){
            groupByKeys.add(key);
        }
    }

    private String getTypeNameOrNullFromMethod(Method value) {
        try {
            //ArrayLists are generics. We need to extract the name of the generic parameter as string and interpret that
            return extractTypeNameFromList(value.getGenericParameterTypes()[0]);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private boolean checkIfNotNullAnnotatedFieldExists(Map<String, Method> methodMap, Class<?> targetClass) throws NoSuchFieldException {
        for (Map.Entry<String, Method> entry : methodMap.entrySet()) {
            Field field = getFieldByName(targetClass, entry.getKey());
            if (field.isAnnotationPresent(NotNull.class)) {
                return true;
            }
        }
        return false;
    }

    private void addGroupByToQueryBuilder(List<String> groupByKeys, StringBuilder queryStringBuilder) {
        if (!groupByKeys.isEmpty()) {
            queryStringBuilder.append("GROUP BY");
            for (String key : groupByKeys) {
                queryStringBuilder.append(" ?").append(key);
            }
        }
    }

    private <T> void addWhereForOtherPropertiesToQueryBuilder(String objectUri, Class<T> targetClass, Map<String, Method> methodMap, StringBuilder queryForOtherProperties) throws NoSuchFieldException {
        queryForOtherProperties.append("WHERE { ")
                .append("<").append(objectUri).append("> ?p ?o .\n");

        //Exclude known properties
        addFilterStatementForOtherPropertiesToQueryBuilder(targetClass, methodMap, queryForOtherProperties);

        queryForOtherProperties.append(")). } ");
    }

    private <T> void addFilterStatementForOtherPropertiesToQueryBuilder(Class<T> targetClass, Map<String, Method> methodMap, StringBuilder queryForOtherProperties) throws NoSuchFieldException {
        queryForOtherProperties.append("FILTER (?p NOT IN (rdf:type");

        //Predicates usually look like: .append("ids:").append(entry.getKey())
        for (Map.Entry<String, Method> entry : methodMap.entrySet()) {
            queryForOtherProperties.append(", ");
            Field field = getFieldByName(targetClass, entry.getKey());
            addSubQueryPropertyToQueryBuilder(queryForOtherProperties, entry.getKey(), field);
        }
    }

    private void addSelectForOtherPropertiesToQueryBuilder(StringBuilder queryForOtherProperties) {
        //Select properties and values only
        queryForOtherProperties.append(" SELECT ?p ?o ");
    }

    private String ignoreSuffix( String s ) {

        if( s.endsWith("AsObject")) {
            return s.replace("AsObject","");
        } else if(s.endsWith("AsUri") ) {
            return s.replace("AsUri","");
        }
        return s;
    }
    /**
     * This function wraps a URI with "<" ">", if needed, to avoid errors about "unknown namespace http(s):"
     * @param input Input URI, possibly a prefixed value
     * @return If this is a full URI, starting with http or https, the URI will be encapsulated in "<" ">"
     */
    private String wrapIfUri(String input)
    {
        if(input.startsWith("http://") || input.startsWith("https://"))
        {
            return "<" + input + ">";
        }
        else {
            return input;
        }
    }

    private Object handleForeignLiteral(Literal literal) throws URISyntaxException {
        if(literal.getLanguage() != null && !literal.getLanguage().equals(""))
        {
            return new TypedLiteral(literal.getString(), literal.getLanguage());
        }
        //If not, does it have some datatype URI?
        else if(literal.getDatatypeURI() != null && !literal.getDatatypeURI().equals(""))
        {
            return new TypedLiteral(literal.getString(), new URI(literal.getDatatypeURI()));
        }
        //If both is not true, add it as normal string
        else
        {
            return literal.getString();
        }
    }

    private HashMap<String, Object> handleForeignNode(RDFNode node, HashMap<String, Object> map, Model model) throws IOException, URISyntaxException {
        //Make sure it is not a literal. If it were, we would not know the property name and could not add this to the map
        //Literals must be handled "one recursion step above"
        if(node.isLiteral())
        {
            throw new IOException("Literal passed to handleForeignNode. Must be non-literal RDF node");
        }

        //Run SPARQL query retrieving all information (only one hop!) about this node
        String queryString = "SELECT ?s ?p ?o { BIND(<" + node.asNode().getURI() + "> AS ?s) . ?s ?p ?o . } ";
        Query query = QueryFactory.create(queryString);
        QueryExecution queryExecution = QueryExecutionFactory.create(query, model);
        ResultSet resultSet = queryExecution.execSelect();



        //Handle outgoing properties of this foreign node
        while(resultSet.hasNext())
        {
            QuerySolution querySolution = resultSet.next();

            String propertyUri = querySolution.get("p").toString();

            if(map.containsKey(propertyUri)) {
                //If it is not an array list yet, turn it into one
                if (!(map.get(propertyUri) instanceof ArrayList)) {
                    ArrayList<Object> newList = new ArrayList<>();
                    newList.add(map.get(propertyUri));
                    map.put(propertyUri, newList);
                }
            }

            //Check the type of object we have. If it is a literal, just add it as "flat value" to the map
            if(querySolution.get("o").isLiteral())
            {
                //Handle some small literal. This function will turn this into a TypedLiteral if appropriate
                Object o = handleForeignLiteral(querySolution.getLiteral("o"));
                if(map.containsKey(propertyUri))
                {
                    map.put(querySolution.get("p").toString(), ((ArrayList)map.get(propertyUri)).add(o));
                }
                else
                {
                    map.put(querySolution.get("p").toString(), o);
                }
            }

            //If it is not a literal, we need to call this function recursively. Create new map for sub object
            else
            {
                //logger.info("Calling handleForeignNode for " + querySolution.getResource("o").toString());
                if(querySolution.getResource("s").toString().equals(querySolution.getResource("o").toString()))
                {
                    logger.warn("Found self-reference on " + querySolution.getResource("s").toString() + " via predicate " + querySolution.getResource("p").toString() + " .");
                    continue;
                }
                HashMap<String, Object> subMap = handleForeignNode(querySolution.getResource("o"), new HashMap<>(), model);
                subMap.put("@id", querySolution.getResource("o").getURI());
                if(map.containsKey(propertyUri))
                {
                    map.put(querySolution.get("p").toString(), ((ArrayList)map.get(propertyUri)).add(subMap));
                }
                else {
                    map.put(querySolution.get("p").toString(), subMap);
                }
            }
        }
        queryExecution.close();
        return map;
    }


    /**
     * Utility function, used to obtain the field corresponding to a setter function
     * @param targetClass Class object in which we search for a field
     * @param fieldName Guessed name of the field to search for
     * @return Field object matching the name (possibly with leading underscore)
     * @throws NoSuchFieldException thrown, if no such field exists
     */
    private Field getFieldByName(Class<?> targetClass, String fieldName) throws NoSuchFieldException {
        try {
            return targetClass.getDeclaredField("_" + fieldName);
        } catch (NoSuchFieldException e) {
            try {
                return targetClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e2) {
                try {
                    return targetClass.getDeclaredField("_" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1));
                }
                catch (NoSuchFieldException e3)
                {
                    throw new NoSuchFieldException("Failed to find field which is set by method " + fieldName);
                }
            }
        }
    }

    /**
     * Internal function to create a single enum object from a given desired class and a URL
     * @param enumClass The enum class
     * @param url The URL of the enum value
     * @param <T> Enum class
     * @return Value of enumClass matching the input URL
     * @throws IOException thrown if no matching enum value could be found
     */
    private <T> T handleEnum(Class<T> enumClass, String url) throws IOException {
        if (!enumClass.isEnum()) {
            throw new RuntimeException("Non-Enum class passed to handleEnum function.");
        }
        T[] constants = enumClass.getEnumConstants();
        for (T constant : constants) {
            if (url.equals(constant.toString())) {
                return constant;
            }
        }
        for(T constant : constants) {
            logger.info("Available enums are: " + constant.toString());
        }
        throw new IOException("Failed to find matching enum value for " + url);
    }

    /**
     * Function for handling a rather primitive object, i.e. not a complex sub-object (e.g. URI, TypedLiteral, GregorianCalendar values, ...)
     * @param currentType Input Class (or primitive)
     * @param literal Value as literal (can be null in some cases)
     * @param currentSparqlBinding Value as SPARQL Binding (can be null in some cases)
     * @return Object of type currentType
     * @throws URISyntaxException thrown, if currentType is URI, but the value cannot be parsed to a URI
     * @throws DatatypeConfigurationException thrown, if currentType is XMLGregorianCalendar or Duration, but parsing fails
     * @throws IOException thrown, if no matching "simple class" could be found
     */
    private Object handlePrimitive(Class<?> currentType, Literal literal, String currentSparqlBinding) throws URISyntaxException, DatatypeConfigurationException, IOException {
        //Java way of checking for primitives, i.e. int, char, float, double, ...
        if (currentType.isPrimitive()) {
            if (literal == null) {
                throw new IOException("Trying to handle Java primitive, but got no literal value");
            }
            //If it is an actual primitive, there is no need to instantiate anything. Just give it to the function
            switch (currentType.getSimpleName()) {
                case "int":
                    return literal.getInt();
                case "boolean":
                    return literal.getBoolean();
                case "long":
                    return literal.getLong();
                case "short":
                    return literal.getShort();
                case "float":
                    return literal.getFloat();
                case "double":
                    return literal.getDouble();
                case "byte":
                    return literal.getByte();
            }
        }

        //Check for the more complex literals

        //URI
        if (URI.class.isAssignableFrom(currentType)) {
            return new URI(currentSparqlBinding);
        }

        //String
        if (String.class.isAssignableFrom(currentType)) {
            return currentSparqlBinding;
        }

        //XMLGregorianCalendar
        if (XMLGregorianCalendar.class.isAssignableFrom(currentType)) {
            //Try parsing this as dateTimeStamp (most specific). If seconds / timezone is missing, DatatypeFormatException will be thrown
            try {
                return DatatypeFactory.newInstance().newXMLGregorianCalendar(GregorianCalendar.from(ZonedDateTime.parse(literal.getValue().toString())));
            }
            catch (DatatypeFormatException | DateTimeParseException ignored)
            {
                //Not a valid dateTimeStamp. Try parsing just to Date
                try {
                    Date date = new SimpleDateFormat().parse(literal.getValue().toString());
                    GregorianCalendar calendar = new GregorianCalendar();
                    calendar.setTime(date);
                    return DatatypeFactory.newInstance().newXMLGregorianCalendar(calendar);
                }
                catch (ParseException | DateTimeParseException | DatatypeFormatException e2)
                {
                    //Do NOT use literal.getValue(), as that can already cause yet another DatatypeFormatException
                    throw new IOException("Could not turn " + literal.getString() + " into " + literal.getDatatypeURI(), e2);
                }
            }
        }

        //TypedLiteral
        if (TypedLiteral.class.isAssignableFrom(currentType)) {
            //Either a language tagged string OR literal with type. Only one allowed
            if (!literal.getLanguage().equals("")) {
                return new TypedLiteral(literal.getValue().toString(), literal.getLanguage());
            }
            if (literal.getDatatypeURI() != null) {
                return new TypedLiteral(literal.getValue().toString(), new URI(literal.getDatatypeURI()));
            }
            return new TypedLiteral(currentSparqlBinding);
        }

        //BigInteger
        if (BigInteger.class.isAssignableFrom(currentType)) {
            return new BigInteger(literal.getString());
        }

        //BigDecimal
        if (BigDecimal.class.isAssignableFrom(currentType)) {
            return new BigDecimal(literal.getString());
        }

        //byte[]
        if (byte[].class.isAssignableFrom(currentType)) {
            return currentSparqlBinding.getBytes();
        }

        //Duration
        if (Duration.class.isAssignableFrom(currentType)) {
            return DatatypeFactory.newInstance().newDuration(currentSparqlBinding);
        }

        //RdfResource
        if (RdfResource.class.isAssignableFrom(currentType)) {
            return new RdfResource(currentSparqlBinding);
        }

        if(Boolean.class.isAssignableFrom(currentType))
        {
            return Boolean.valueOf(currentSparqlBinding);
        }

        if(Long.class.isAssignableFrom(currentType))
        {
            return Long.valueOf(currentSparqlBinding);
        }

        throw new IOException("Unrecognized primitive type: " + currentType.getName());
    }

    /**
     * This list contains all primitive Java types
     */
    private final Map<String, Class<?>> builtInMap = new HashMap<>();
    {
        builtInMap.put("int", Integer.TYPE);
        builtInMap.put("long", Long.TYPE);
        builtInMap.put("double", Double.TYPE);
        builtInMap.put("float", Float.TYPE);
        builtInMap.put("bool", Boolean.TYPE);
        builtInMap.put("char", Character.TYPE);
        builtInMap.put("byte", Byte.TYPE);
        builtInMap.put("void", Void.TYPE);
        builtInMap.put("short", Short.TYPE);
    }

    private boolean isArrayListTypePrimitive(Type t) throws IOException {
        String typeName = extractTypeNameFromList(t);

        try {
            //Do not try to call Class.forName(primitive) -- that would throw an exception
            if (builtInMap.containsKey(typeName)) return true;
            return isPrimitive(Class.forName(typeName));
        } catch (ClassNotFoundException e) {
            throw new IOException("Unable to retrieve class from generic", e);
        }
    }

    private String extractTypeNameFromList(Type t) throws IOException {
        String typeName = t.getTypeName();
        if (!typeName.startsWith("java.util.ArrayList<") && !typeName.startsWith("java.util.List<")) {
            throw new IOException("Illegal argument encountered while interpreting type parameter");
        }
        //"<? extends XYZ>" or super instead of extends
        if(typeName.contains("?"))
        {
            //last space is where we want to cut off (right after the "extends"), as well as removing the last closing braces
            return typeName.substring(typeName.lastIndexOf(" ") + 1, typeName.length() - 1);
        }
        //No extends
        else
        {
            return typeName.substring(typeName.indexOf("<") + 1, typeName.indexOf(">"));
        }
    }

    private boolean isPrimitive(Class<?> input) throws IOException {
        //Collections are not simple
        if (Collection.class.isAssignableFrom(input)) {
            throw new IOException("Encountered collection in isPrimitive. Use isArrayListTypePrimitive instead");
        }

        //check for: plain/typed literal, XMLGregorianCalendar, byte[], RdfResource
        //covers int, long, short, float, double, boolean, byte
        if (input.isPrimitive()) return true;

        return (URI.class.isAssignableFrom(input) ||
                String.class.isAssignableFrom(input) ||
                XMLGregorianCalendar.class.isAssignableFrom(input) ||
                TypedLiteral.class.isAssignableFrom(input) ||
                BigInteger.class.isAssignableFrom(input) ||
                BigDecimal.class.isAssignableFrom(input) ||
                byte[].class.isAssignableFrom(input) ||
                Duration.class.isAssignableFrom(input) ||
                RdfResource.class.isAssignableFrom(input)) ||
                Boolean.class.isAssignableFrom(input) ||
                Long.class.isAssignableFrom(input);
    }

    /**
     * Entry point to this class. Takes an RDF Model and a desired target class (can be an interface)
     * @param rdfModel RDF input to be parsed
     * @param targetClass Desired target class (something as abstract as "Message.class" is allowed)
     * @param <T> Desired target class
     * @return Object of desired target class, representing the values contained in input message
     * @throws IOException if the parsing of the message fails
     */
    <T> T parseMessage(Model rdfModel, Class<T> targetClass) throws IOException {
        ArrayList<Class<?>> implementingClasses = getImplementingClasses(targetClass);

        // Query to retrieve all instances in the input graph that have a class assignment
        // Assumption: if the class name (?type) is equal to the target class, this should be the
        // instance we actually want to parse
        String queryString = "SELECT ?id ?type { ?id a ?type . }";
        Query query = QueryFactory.create(queryString);
        QueryExecution queryExecution = QueryExecutionFactory.create(query, rdfModel);
        ResultSet resultSet = queryExecution.execSelect();

        if (!resultSet.hasNext()) {
            throw new IOException("Could not extract class from input message");
        }

        Map<String, Class<?>> returnCandidates = new HashMap<>();

        while (resultSet.hasNext()) {
            QuerySolution solution = resultSet.nextSolution();
            String fullName = solution.get("type").toString();
            String className = fullName.substring(fullName.lastIndexOf('/') + 1);

            //In case of hash-namespaces
            if(className.contains("#")) {
                className = className.substring(className.lastIndexOf("#"));
            }

            //For legacy purposes...
            if (className.startsWith("ids:")) {
                className = className.substring(4);
            }

            for (Class<?> currentClass : implementingClasses) {
                if (currentClass.getSimpleName().equals(Serializer.implementingClassesNamePrefix + className + Serializer.implementingClassesNameSuffix)) {
                    returnCandidates.put(solution.get("id").toString(), currentClass);
                }
            }
            //if (returnCandidates.size() > 0) break;
        }
        queryExecution.close();

        if (returnCandidates.size() == 0) {
            throw new IOException("Could not transform input to an appropriate implementing class for " + targetClass.getName());
        }

        //At this point, we parsed the model and know to which implementing class we want to parse
        //Check if there are several options available
        if(returnCandidates.size() > 1)
        {
            String bestCandidateId = null;
            Class<?> bestCandidateClass = null;
            long bestNumRelations = -1L;
            for(Map.Entry<String, Class<?>> entry : returnCandidates.entrySet())
            {
                String determineBestCandidateQueryString = "CONSTRUCT { ?s ?p ?o . ?o ?p2 ?o2 . ?o2 ?p3 ?o3 . ?o3 ?p4 ?o4 . ?o4 ?p5 ?o5 . }" +
                        " WHERE {" +
                        " BIND(<" + entry.getKey() + "> AS ?s). ?s ?p ?o ." +
                        " OPTIONAL {?o ?p2 ?o2 . OPTIONAL {?o2 ?p3 ?o3 . OPTIONAL {?o3 ?p4 ?o4 . OPTIONAL {?o4 ?p5 ?o5 . } } } } }";
                Query determineBestCandidateQuery = QueryFactory.create(determineBestCandidateQueryString);
                QueryExecution determineBestCandidateQueryExecution = QueryExecutionFactory.create(determineBestCandidateQuery, rdfModel);
                long graphSize = determineBestCandidateQueryExecution.execConstruct().size();
                if(graphSize > bestNumRelations)
                {
                    bestNumRelations = graphSize;
                    bestCandidateId = entry.getKey();
                    bestCandidateClass = entry.getValue();
                }

                determineBestCandidateQueryExecution.close();

            }
            logger.debug("The RDF graph contains multiple objects which can be parsed to " + targetClass.getSimpleName() + ". Determined " + bestCandidateId + " as best candidate.");
            return (T) handleObject(rdfModel, bestCandidateId, bestCandidateClass);
        }

        //We only reach this spot, if there is exactly one return candidate. Let's return it
        Map.Entry<String, Class<?>> singularEntry = returnCandidates.entrySet().iterator().next();
        return (T) handleObject(rdfModel, singularEntry.getKey(), singularEntry.getValue());

    }


    /**
     * Entry point to this class. Takes a message and a desired target class (can be an interface)
     * @param message Object to be parsed. Note that the name is misleading: One can also parse non-message IDS objects with this function
     * @param targetClass Desired target class (something as abstract as "Message.class" is allowed)
     * @param <T> Desired target class
     * @return Object of desired target class, representing the values contained in input message
     * @throws IOException if the parsing of the message fails
     */
    <T> T parseMessage(String message, Class<T> targetClass) throws IOException {
        Model model = readMessage(message);
        return parseMessage(model, targetClass);
    }

    /**
     * Reads a message into an Apache Jena model.
     *
     * @param message Message to be read
     * @return The model of the message
     */
    private Model readMessage(String message) throws IOException {

        Model targetModel = ModelFactory.createDefaultModel();

        //Read incoming message to the same model
        try {
            RDFDataMgr.read(targetModel, new ByteArrayInputStream(message.getBytes()), RDFLanguages.JSONLD);
        }
        catch (RiotException e)
        {
            throw new IOException("The message is no valid JSON-LD and therefore could not be parsed.", e);
        }

        return targetModel;
    }


    /**
     * Get a list of all subclasses (by JsonSubTypes annotation) which can be instantiated
     * @param someClass Input class of which implementable subclasses need to be found
     * @return ArrayList of instantiable subclasses
     */
    ArrayList<Class<?>> getImplementingClasses(Class<?> someClass) {
        ArrayList<Class<?>> result = new ArrayList<>();
        JsonSubTypes subTypeAnnotation = someClass.getAnnotation(JsonSubTypes.class);
        if (subTypeAnnotation != null) {
            JsonSubTypes.Type[] types = subTypeAnnotation.value();
            for (JsonSubTypes.Type type : types) {
                result.addAll(getImplementingClasses(type.value()));
            }
        }
        if (!someClass.isInterface() && !Modifier.isAbstract(someClass.getModifiers()))
            result.add(someClass);
        return result;
    }

    private boolean checkForTypeStatement(Model inputModel, String objectUri, Class<?> targetClass) {
        if (targetClass.isInterface() || Modifier.isAbstract(targetClass.getModifiers())) {
            //We don't know the desired class yet (current targetClass is not instantiable). This is only known for the root object
            ArrayList<Class<?>> implementingClasses = getImplementingClasses(targetClass);

            //Get a list of all "rdf:type" statements in our model
            try {
                URI uri = URI.create(objectUri);
            } catch (IllegalArgumentException uriSyntaxException) {
                return false;
            }
            String queryString = "SELECT ?type { BIND(<" + objectUri + "> AS ?s). ?s a ?type . }";
            try {
                Query query = QueryFactory.create(queryString);
                QueryExecution queryExecution = QueryExecutionFactory.create(query, inputModel);
                ResultSet resultSet = queryExecution.execSelect();
                if (!resultSet.hasNext()) {
                    queryExecution.close();
                    return false;
                }
            } catch (QueryException exception) {
                return false;
            }
        }
        return true;
    }

    private boolean checkIfClassIsDefaultInterfaceInstance(String className, Model inputModel, String currentSparqlBinding) {
        try {
            Class<?> clazz = Class.forName(className);
            // Check if type statement exists. If so, this is can be handled as normal class instance.
            if (checkForTypeStatement(inputModel, currentSparqlBinding, clazz)) {
                return false;
            }
            // else, check if the id value of the current object matches one of the default instance values
            try {
                List<URI> ids = new ArrayList<>();
                try {
                    for (Field f : clazz.getFields()) {
                        Object individual = f.get(null);
                        Method m = clazz.getMethod("getId");
                        ids.add((URI) m.invoke(individual));
                    }

                    URI csbAsUri = URI.create(currentSparqlBinding);
                    if (ids.contains(csbAsUri)) {
                        return true;
                    }
                } catch (IllegalArgumentException exception) {
                    return false;
                }
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException exception) {
                return false;
            }
        } catch (ClassNotFoundException exception) {
            return false;
        }
        return false;
    }

    private Object getDefaultInterfaceInstance(String className, String currentSparqlBinding) {
        try {
            Class<?> clazz = Class.forName(className);
            try {
                for (Field f : clazz.getFields()) {
                    Object individual = f.get(null);
                    Method m = clazz.getMethod("getId");
                    URI individualAsUri =((URI) m.invoke(individual));
                    if (individualAsUri.equals(URI.create(currentSparqlBinding))) {
                        return individual;
                    }
                }
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException exception) {
                return null;
            }
        } catch (ClassNotFoundException classNotFoundException) {
            return null;
        }
        return null;
    }

}
