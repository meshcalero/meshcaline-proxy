package org.meshcaline.proxy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import graphql.language.*;
import graphql.parser.Parser;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Stream;

@Service
public class ProxyQueryProcessor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Result process(JsonNode sourceNode, String filterQuery) {
        return processDocument(
            sourceNode,
            new Parser().parseDocument(filterQuery)
        );
    }

    private record RootOperation (
        @Nullable String name,
        SelectionSet selectionSet,
        List<Directive> directives
    ){
        public static RootOperation of(OperationDefinition queryOperation){
            return new RootOperation(
                queryOperation.getName(),
                queryOperation.getSelectionSet(),
                queryOperation.getDirectives()
            );
        }
    }

    private Result processDocument(JsonNode sourceNode, Document document) {
        return document
            .getDefinitionsOfType(OperationDefinition.class)
            .stream()
            .filter( opDef -> OperationDefinition.Operation.QUERY.equals(opDef.getOperation()) )
            .findFirst()
            .map( queryOperation ->
                processDocumentRoot(RootOperation.of(queryOperation), sourceNode, document)
            )
            // if the graphQL doesn't contain a query operation,
            // ignore the query and return the source
            .orElse( new Result(sourceNode.deepCopy(), Collections.emptyList()) );
    }

    private Result processDocumentRoot(
        RootOperation queryOperation,
        JsonNode sourceNode,
        Document document
    ) {
        final Result result;
        if( sourceNode.isArray() ){
            final ArrayNode targetArray = objectMapper.createArrayNode();
            final ArrayNode sourceArray = (ArrayNode) sourceNode;
            result = new Result(targetArray, new ArrayList<FollowUpTask>());
            processSelectionSetOnArray(
                queryOperation.selectionSet(), queryOperation.name(), queryOperation.directives(), sourceArray, document, targetArray, result.getFollowUpTasks()
            );
        }
        else if( sourceNode.isObject() ) {
            final ObjectNode targetObject = objectMapper.createObjectNode();
            final ObjectNode sourceObject = (ObjectNode) sourceNode;
            result = new Result(targetObject, new ArrayList<FollowUpTask>());
            processSelectionSetOnObject(
                queryOperation.selectionSet(), queryOperation.name(), queryOperation.directives(), sourceObject, document, targetObject, result.getFollowUpTasks()
            );
        }
        else {
            throw new UnsupportedOperationException("Support for JSON documents restricted to Object and Arrays: "+ sourceNode);
        }
        return result;
    }

    public void processSelectionSetOnObject(
        SelectionSet selectionSet,
        @Nullable String parentName,
        List<Directive> directives,
        ObjectNode sourceNode,
        Document document,
        ObjectNode targetNode,
        List<FollowUpTask> followUpTasks
    ) {
        extractApplicableFields(selectionSet, parentName, sourceNode, document)
            .forEach( field -> {
                processField(sourceNode, field, document, targetNode, followUpTasks);
            });
        processGetDirective(directives, document, followUpTasks, parentName, sourceNode);
    }

    public void processSelectionSetOnArray(
        SelectionSet selectionSet,
        @Nullable String parentName,
        List<Directive> directives,
        ArrayNode sourceNode,
        Document document,
        ArrayNode targetNode,
        List<FollowUpTask> followUpTasks
    ) {
        for( JsonNode element: sourceNode) {
            if (element.isObject()) {
                final ObjectNode targetObject = objectMapper.createObjectNode();
                processSelectionSetOnObject(
                    selectionSet, parentName, directives, (ObjectNode) element, document, targetObject, followUpTasks
                );
                targetNode.add(targetObject);
            } else if (element.isArray()) {
                ArrayNode targetArray = objectMapper.createArrayNode();
                processSelectionSetOnArray(
                    selectionSet, parentName, directives, (ArrayNode) element, document, targetArray, followUpTasks
                );
                targetNode.add(targetArray);
            } else {
                throw new UnsupportedOperationException("Support for top level JSON arrays elements restricted to Object and Array: "+sourceNode);
            }
        }
    }

    private Stream<Field> extractApplicableFields(
            SelectionSet selectionSet,
            @Nullable String parentName,
            JsonNode sourceNode,
            Document document
    ) {
        return selectionSet.getSelections().stream()
            .flatMap( selection -> {
                if (Field.class.isAssignableFrom(selection.getClass())) {
                    return Stream.of((Field) selection);
                }
                else if (FragmentSpread.class.isAssignableFrom(selection.getClass())) {
                    final FragmentSpread fragmentSpread = (FragmentSpread) selection;
                    final Optional<String> requiredType = searchRequiredType(sourceNode, parentName);
                    return searchApplicableFragment(document, fragmentSpread.getName(), requiredType)
                        .stream()
                        .flatMap(fragment ->
                            extractApplicableFields(fragment.getSelectionSet(), parentName, sourceNode, document)
                        );
                }
                else if (InlineFragment.class.isAssignableFrom(selection.getClass())) {
                    final InlineFragment inlineFragment = (InlineFragment) selection;
                    final Optional<String> requiredType = searchRequiredType(sourceNode, parentName);
                    if( isTypeMatch(inlineFragment.getTypeCondition(), requiredType)) {
                        return extractApplicableFields(inlineFragment.getSelectionSet(), parentName, sourceNode, document);
                    }
                }
                return Stream.empty();
            });
    }

    private Optional<String> searchRequiredType(JsonNode sourceNode, String defaultType){
        return Optional.ofNullable(sourceNode.get("type"))
                .map(JsonNode::asText)
                .or( ()-> Optional.ofNullable(defaultType) );
    }
    private Optional<FragmentDefinition> searchApplicableFragment(
            @NonNull Document document,
            @NonNull String fragmentName,
            @NonNull Optional<String> requiredType
    ) {
        return document.getDefinitionsOfType(FragmentDefinition.class).stream()
                .filter(fragment ->
                        fragmentName.equals(fragment.getName())
                            && isTypeMatch(fragment.getTypeCondition(), requiredType)
                )
                .findFirst();
    }

    private boolean isTypeMatch(@NonNull TypeName typeName, @NonNull Optional<String> requiredType) {
        return requiredType
                .map(t -> t.equals(typeName.getName()))
                .orElse(true);
    }

    public Result processFollowUpTask(JsonNode sourceNode, FollowUpTask followUpTask) {
        return processDocumentRoot(
            followUpTask.rootOperation(),
            sourceNode,
            followUpTask.document()
        );
    }

    private void processField(
            JsonNode sourceNode,
            Field field,
            Document document,
            ObjectNode targetNode,
            List<FollowUpTask> followUpTasks
    ) {
        String fieldName = field.getName();
        String alias = field.getAlias() != null ? field.getAlias() : fieldName;
        if (sourceNode.has(fieldName)) {
            JsonNode fieldValue = sourceNode.get(fieldName);
            if (fieldValue.isObject()) {
                targetNode.set(
                    alias,
                    processObjectField(field, document, followUpTasks, fieldName, (ObjectNode) fieldValue)
                );
            } else if (fieldValue.isArray()) {
                ArrayNode filteredArrayNode = objectMapper.createArrayNode();
                processArrayElements(
                    fieldValue, field, document, filteredArrayNode, followUpTasks
                );
                targetNode.set(alias, filteredArrayNode);
            } else {
                targetNode.set(alias, fieldValue.deepCopy());
            }
        }
        else {
            throw new RuntimeException("Response is missing json attribute:" + fieldName);
        }
    }

    private ObjectNode processObjectField(
            Field field,
            Document document,
            List<FollowUpTask> followUpTasks,
            String fieldName,
            ObjectNode fieldValue
    ) {
        ObjectNode filteredFieldValue = objectMapper.createObjectNode();
        processSelectionSetOnObject(
            field.getSelectionSet(), fieldName, field.getDirectives(), fieldValue, document, filteredFieldValue, followUpTasks
        );
        return filteredFieldValue;
    }

    private void processGetDirective(List<Directive> directives, Document document, List<FollowUpTask> followUpTasks, String fieldName, JsonNode fieldValue) {
        directives.stream()
            .filter( d -> "GET".equals(d.getName()) )
            .findFirst()
            .ifPresent( dir -> {
                final String fragmentName = findDirectiveArgumentValue(dir, "fragment")
                    .orElseThrow( () -> new IllegalArgumentException("missing fragment argument for GET directive of field "+ fieldName) );
                final String uriTemplate = Optional.ofNullable(fieldValue.get("href"))
                    .map(JsonNode::asText)
                    .or( () -> findDirectiveArgumentValue(dir, "href") )
                    .orElseThrow( () -> new IllegalArgumentException("missing href attribute for GET directive of field "+ fieldName) );
                final String uri = new StringSubstitutor(key -> fieldValue.get(key).asText()) //TODO: Handle nested fields
                    .replace(uriTemplate);
                final Optional<String> fragmentTypeOpt = findDirectiveArgumentValue(dir, "type");
                final FragmentDefinition fragment =
                    searchApplicableFragment(document, fragmentName, fragmentTypeOpt)
                        .orElseThrow( () -> new IllegalArgumentException("missing fragment " + fragmentName ) );
                followUpTasks.add(new FollowUpTask(
                    new RootOperation(
                        fragment.getTypeCondition().getName(),
                        fragment.getSelectionSet(),
                        fragment.getDirectives()
                    ),
                    fieldValue,
                    document,
                    uri
                ));
            });
    }

    private Optional<String> findDirectiveArgumentValue(Directive directive, String argumentName) {
        return Optional.ofNullable(directive.getArgument(argumentName))
            .map( arg -> ((StringValue) arg.getValue()).getValue() );
    }

    private void processArrayElements(
            JsonNode arrayNode,
            Field field,
            Document document,
            ArrayNode targetNode,
            List<FollowUpTask> followUpTasks
    ) {

        for (JsonNode element : arrayNode) {
            if (element.isObject()) {
                targetNode.add( processObjectField(
                        field, document, followUpTasks, field.getName(), (ObjectNode)element)
                );
            } else if (element.isArray()) {
                ArrayNode filteredArrayNode = objectMapper.createArrayNode();
                processArrayElements(
                        element, field, document, filteredArrayNode, followUpTasks
                );
                targetNode.add(filteredArrayNode);
            } else {
                targetNode.add(element.deepCopy());
            }
        }
    }

    public static class Result {
        private final JsonNode targetNode;
        private final List<FollowUpTask> followUpTasks;

        public Result(JsonNode targetNode, List<FollowUpTask> followUpTasks) {
            this.targetNode = targetNode;
            this.followUpTasks = followUpTasks;
        }

        public JsonNode getTargetNode() {
            return targetNode;
        }

        public List<FollowUpTask> getFollowUpTasks() {
            return followUpTasks;
        }
    }

    public record FollowUpTask (
        RootOperation rootOperation,
        JsonNode sourceNode,
        Document document,
        String url
    ){
    }
}
