package jamsnes;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildConfigurationTest {
    private static final String MAVEN_NAMESPACE = "http://maven.apache.org/POM/4.0.0";

    @Test
    void lwjglNativeDependenciesUseConfigurableClassifier() throws Exception {
        Document pom = pom();
        NodeList dependencies = pom.getElementsByTagNameNS(MAVEN_NAMESPACE, "dependency");
        int nativeDependencyCount = 0;

        for (int i = 0; i < dependencies.getLength(); i++) {
            Element dependency = (Element) dependencies.item(i);
            if (!"org.lwjgl".equals(childText(dependency, "groupId")) || childText(dependency, "classifier").isBlank()) {
                continue;
            }

            nativeDependencyCount++;
            assertEquals("${lwjgl.natives}", childText(dependency, "classifier"));
            assertEquals("runtime", childText(dependency, "scope"));
        }

        assertEquals(4, nativeDependencyCount);
    }

    @Test
    void lwjglNativeProfilesCoverMainDesktopTargets() throws Exception {
        Map<String, String> profileNatives = lwjglProfileNatives(pom());

        assertEquals("natives-macos-arm64", profileNatives.get("lwjgl-natives-macos-arm64"));
        assertEquals("natives-macos", profileNatives.get("lwjgl-natives-macos-x64"));
        assertEquals("natives-linux", profileNatives.get("lwjgl-natives-linux-x64"));
        assertEquals("natives-windows", profileNatives.get("lwjgl-natives-windows-x64"));
    }

    @Test
    void packageBuildProducesExecutableJamSNESJar() throws Exception {
        Document pom = pom();
        Element build = (Element) pom.getElementsByTagNameNS(MAVEN_NAMESPACE, "build").item(0);
        Element shadePlugin = plugin(pom, "org.apache.maven.plugins", "maven-shade-plugin");

        assertEquals("jamsnes", childText(build, "finalName"));
        assertNotNull(shadePlugin);
        assertEquals("3.6.2", childText(shadePlugin, "version"));
        assertEquals("package", childText(shadePlugin, "phase"));
        assertEquals("shade", childText(shadePlugin, "goal"));
        assertEquals("false", childText(shadePlugin, "createDependencyReducedPom"));
        assertEquals("jamsnes.Main", childText(shadePlugin, "mainClass"));
    }

    private static Document pom() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(Path.of("pom.xml").toFile());
        document.getDocumentElement().normalize();
        return document;
    }

    private static Map<String, String> lwjglProfileNatives(Document pom) {
        Map<String, String> profileNatives = new HashMap<>();
        NodeList profiles = pom.getElementsByTagNameNS(MAVEN_NAMESPACE, "profile");
        for (int i = 0; i < profiles.getLength(); i++) {
            Element profile = (Element) profiles.item(i);
            String id = childText(profile, "id");
            if (id.startsWith("lwjgl-natives-")) {
                profileNatives.put(id, childText(profile, "lwjgl.natives"));
            }
        }
        return profileNatives;
    }

    private static Element plugin(Document pom, String groupId, String artifactId) {
        NodeList plugins = pom.getElementsByTagNameNS(MAVEN_NAMESPACE, "plugin");
        for (int i = 0; i < plugins.getLength(); i++) {
            Element plugin = (Element) plugins.item(i);
            if (groupId.equals(childText(plugin, "groupId"))
                    && artifactId.equals(childText(plugin, "artifactId"))) {
                return plugin;
            }
        }
        return null;
    }

    private static String childText(Element parent, String childName) {
        NodeList children = parent.getElementsByTagNameNS(MAVEN_NAMESPACE, childName);
        assertTrue(children.getLength() <= 1, "Expected at most one " + childName);
        return children.getLength() == 0 ? "" : children.item(0).getTextContent();
    }
}
