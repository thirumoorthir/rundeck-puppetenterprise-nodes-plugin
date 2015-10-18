/*
 * Copyright 2011 DTO Solutions, Inc. (http://dtosolutions.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
* NodeGenerator.java
* 
* User: Greg Schueler <a href="mailto:greg@dtosolutions.com">greg@dtosolutions.com</a>
* Created: Oct 18, 2010 7:03:37 PM
* 
*/
package com.rundeck.plugin.resources.puppetdb;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Objects.isNull;
import static java.util.Optional.empty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.common.NodeEntryImpl;
import com.rundeck.plugin.resources.puppetdb.client.model.NodeWithFacts;
import org.apache.commons.beanutils.PropertyUtilsBean;
import org.apache.log4j.Logger;

/**
 * InstanceToNodeMapper produces Rundeck node definitions from EC2 Instances
 *
 * @author Greg Schueler <a href="mailto:greg@dtosolutions.com">greg@dtosolutions.com</a>
 */
public class Mapper implements BiFunction<NodeWithFacts, Map<String, Map<String, Object>>, Optional<INodeEntry>> {

    private static final List<String> REQUIRED_PROPERTIES = asList("hostname", "username", "nodename", "tags");
    private static Logger log = Logger.getLogger(ResourceModelFactory.class);

    private final PropertyUtilsBean propertyUtilsBean;

    public Mapper() {
        this.propertyUtilsBean = new PropertyUtilsBean();
    }


    @Override
    public Optional<INodeEntry> apply(final NodeWithFacts node,
                                      final Map<String, Map<String, Object>> mappings) {
        // 1.- create a new instance
        final NodeEntryImpl result = newNodeTreeImpl();

        // 2.- using configuration map everything
        mappings.forEach((propertyName, mapping) -> {
            final boolean isCollection = "tags".equals(propertyName) ||
                    "attributes".equals(propertyName);

            if (isCollection) {
                return;
            }

            final boolean isDefault = mapping.containsKey("default");
            final boolean isPath = mapping.containsKey("path");

            if (isDefault && isPath) {
                // TODO: flag problem
                final String template = "property: '%s' is misconfigured, " +
                        "can't have default and path properties at the same time";
                log.warn(template);
                return;
            }

            if (isDefault) {
                final String value = mapping.getOrDefault("default", "").toString();
                if (isNotBlank(value)) {
                    setNodeProperty(result, propertyName, value);
                }
            }

            if (isPath) {
                final String path = mapping.getOrDefault("path", "").toString();
                if (isNotBlank(path)) {
                    final String value = getNodeWithFactsProperty(node, path);
                    if (isNotBlank(value)) {
                        setNodeProperty(result, propertyName, value);
                    }
                }
            }
        });

        // 3.- check if valid
        return validState(result) ? Optional.of(result) : empty();
    }

    private String getNodeWithFactsProperty(final NodeWithFacts nodeWithFacts,
                                            final String propertyPath) {
        if (isBlank(propertyPath)) {
            return "";
        }

        try {
            final Object value = propertyUtilsBean.getProperty(nodeWithFacts, propertyPath);

            return isNull(value) ? "" : value.toString();
        } catch (IllegalAccessException|InvocationTargetException|NoSuchMethodException e) {
            final String template = "can't parse propertyPath: '%s'";
            final String message = format(template, propertyPath);
            log.warn(message, e);
        }

        return "";
    }

    private void setNodeProperty(final NodeEntryImpl nodeEntry,
                                 final String propertyName,
                                 final String value) {
        if (isBlank(propertyName) || isBlank(value)) {
            return;
        }

        try {
            propertyUtilsBean.setProperty(nodeEntry, propertyName, value);
        } catch (IllegalAccessException|InvocationTargetException|NoSuchMethodException e) {
            e.printStackTrace();
            final String template = "can't set NodeEntry property '%s', value: '%s'";
            final String message = format(template, propertyName, value);
            log.warn(message, e);
        }
    }

    private NodeEntryImpl newNodeTreeImpl() {
        final NodeEntryImpl result = new NodeEntryImpl();

        if (isNull(result.getTags())) {
            result.setTags(new LinkedHashSet<>());
        }

        if (isNull(result.getAttributes())) {
            result.setAttributes(new LinkedHashMap<>());
        }

        return result;
    }

    private boolean validState(final INodeEntry nodeEntry) {
        if (isNull(nodeEntry)) {
            return false;
        }

        return true;
    }


    /*
    private final Properties mapping;

    /**
     * Create with the credentials and mapping definition
    InstanceToNodeMapper(final Properties mapping) {
        this.mapping = mapping;
    }

    /**
     * Convert an AWS EC2 Instance to a RunDeck INodeEntry based on the mapping input
    static INodeEntry instanceToNode(final Properties mapping) throws GeneratorException {
        final NodeEntryImpl node = new NodeEntryImpl();



        //evaluate single settings.selector=tags/* mapping
        if ("tags/*".equals(mapping.getProperty("attributes.selector"))) {
            System.out.println("primer filtro alcanzado por: " + mapping.getProperty("attributes.selector"));

        }

        if (null != mapping.getProperty("tags.selector")) {
            final String selector = mapping.getProperty("tags.selector");
            final String value = applySelector(inst, selector, mapping.getProperty("tags.default"), true);
            if (null != value) {
                final String[] values = value.split(",");
                final HashSet<String> tagset = new HashSet<String>();
                for (final String s : values) {
                    tagset.add(s.trim());
                }
                if (null == node.getTags()) {
                    node.setTags(tagset);
                } else {
                    final HashSet orig = new HashSet(node.getTags());
                    orig.addAll(tagset);
                    node.setTags(orig);
                }
            }
        }
        if (null == node.getTags()) {
            node.setTags(new HashSet());
        }
        final HashSet orig = new HashSet(node.getTags());
        //apply specific tag selectors
        final Pattern tagPat = Pattern.compile("^tag\\.(.+?)\\.selector$");
        //evaluate tag selectors
        for (final Object o : mapping.keySet()) {
            final String key = (String) o;
            final String selector = mapping.getProperty(key);
            //split selector by = if present
            final String[] selparts = selector.split("=");
            final Matcher m = tagPat.matcher(key);
            if (m.matches()) {
                final String tagName = m.group(1);
                if (null == node.getAttributes()) {
                    node.setAttributes(new HashMap<String, String>());
                }
                final String value = applySelector(inst, selparts[0], null);
                if (null != value) {
                    if (selparts.length > 1 && !value.equals(selparts[1])) {
                        continue;
                    }
                    //use add the tag if the value is not null
                    orig.add(tagName);
                }
            }
        }
        node.setTags(orig);

        //apply default values which do not have corresponding selector
        final Pattern attribDefPat = Pattern.compile("^([^.]+?)\\.default$");
        //evaluate selectors
        for (final Object o : mapping.keySet()) {
            final String key = (String) o;
            final String value = mapping.getProperty(key);
            final Matcher m = attribDefPat.matcher(key);
            if (m.matches() && (!mapping.containsKey(key + ".selector") || "".equals(mapping.getProperty(
                key + ".selector")))) {
                final String attrName = m.group(1);
                if (null == node.getAttributes()) {
                    node.setAttributes(new HashMap<String, String>());
                }
                if (null != value) {
                    node.getAttributes().put(attrName, value);
                }
            }
        }

        final Pattern attribPat = Pattern.compile("^([^.]+?)\\.selector$");
        //evaluate selectors
        for (final Object o : mapping.keySet()) {
            final String key = (String) o;
            final String selector = mapping.getProperty(key);
            final Matcher m = attribPat.matcher(key);
            if (m.matches()) {
                final String attrName = m.group(1);
                if(attrName.equals("tags")){
                    //already handled
                    continue;
                }
                if (null == node.getAttributes()) {
                    node.setAttributes(new HashMap<String, String>());
                }
                final String value = applySelector(inst, selector, mapping.getProperty(attrName + ".default"));
                if (null != value) {
                    //use nodename-settingname to make the setting unique to the node
                    node.getAttributes().put(attrName, value);
                }
            }
        }
//        String hostSel = mapping.getProperty("hostname.selector");
//        String host = applySelector(inst, hostSel, mapping.getProperty("hostname.default"));
//        if (null == node.getHostname()) {
//            System.err.println("Unable to determine hostname for instance: " + inst.getInstanceId());
//            return null;
//        }
        String name = node.getNodename();
        if (null == name || "".equals(name)) {
            name = node.getHostname();
        }
        if (null == name || "".equals(name)) {
            name = inst.getInstanceId();
        }
        node.setNodename(name);

        return node;
    }

    /**
     * Return the result of the selector applied to the instance, otherwise return the defaultValue. The selector can be
     * a comma-separated list of selectors
    public static String applySelector(final Instance inst, final String selector, final String defaultValue) throws
        GeneratorException {
        return applySelector(inst, selector, defaultValue, false);
    }

    /**
     * Return the result of the selector applied to the instance, otherwise return the defaultValue. The selector can be
     * a comma-separated list of selectors.
     * @param inst the instance
     * @param selector the selector string
     * @param defaultValue a default value to return if there is no result from the selector
     * @param tagMerge if true, allow | separator to merge multiple values
    public static String applySelector(final Instance inst, final String selector, final String defaultValue,
                                       final boolean tagMerge) throws
        GeneratorException {

        if (null != selector) {
            for (final String selPart : selector.split(",")) {
                if (tagMerge) {
                    final StringBuilder sb = new StringBuilder();
                    for (final String subPart : selPart.split(Pattern.quote("|"))) {
                        final String val = applySingleSelector(inst, subPart);
                        if (null != val) {
                            if (sb.length() > 0) {
                                sb.append(",");
                            }
                            sb.append(val);
                        }
                    }
                    if (sb.length() > 0) {
                        return sb.toString();
                    }
                } else {
                    final String val = applySingleSelector(inst, selPart);
                    if (null != val) {
                        return val;
                    }
                }
            }
        }
        return defaultValue;
    }

    private static String applySingleSelector(final Instance inst, final String selector) throws
        GeneratorException {
        if (null != selector && !"".equals(selector) && selector.startsWith("tags/")) {
            final String tag = selector.substring("tags/".length());
            final List<Tag> tags = inst.getTags();
            for (final Tag tag1 : tags) {
                if (tag.equals(tag1.getKey())) {
                    return tag1.getValue();
                }
            }
        } else if (null != selector && !"".equals(selector)) {
            try {
                final String value = BeanUtils.getProperty(inst, selector);
                if (null != value) {
                    return value;
                }
            } catch (Exception e) {
                throw new GeneratorException(e);
            }
        }

        return null;
    }
    */

}