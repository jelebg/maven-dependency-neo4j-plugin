package org.apache.maven.plugin.dependency.neo4j;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.traversal.DependencyNodeVisitor;
import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.Values;

/**
 * A dependency node visitor that serializes visited nodes to <a href="https://en.wikipedia.org/wiki/DOT_language">DOT
 * format</a>
 *
 * @author <a href="mailto:pi.songs@gmail.com">Pi Song</a>
 * @since 2.1
 */
public class Neo4jDependencyNodeVisitor
    implements DependencyNodeVisitor
{

    private Transaction tx;
    private Log log;

    /**
     * Constructor.
     *
     * @ param writer the writer to write to.
     */
    public Neo4jDependencyNodeVisitor(Transaction tx, Log log)
    {
        this.tx = tx;
        // TODO : créer les indexs quand nécessaire
        this.log = log;

    }

    @Override
    public boolean visit(org.apache.maven.shared.dependency.tree.DependencyNode node) {

        log.info("");
        log.info("node: " + node.getArtifact().getArtifactId() + " - " + node.getArtifact().getVersion() + " - " + node.getArtifact().getType());

        // TODO : prepare statement
        tx.run(
                String.join("\n",
                        "MERGE (parentAV:ArtifactAllVersion {groupId:$groupId, artifactId:$artifactId, type:$atype})",
                        "MERGE (parent:Artifact {groupId:$groupId, artifactId:$artifactId, type:$atype, version:$version})",
                        "MERGE (parent) <-[:VERSION]- (parentAV)"),
                Values.parameters(
                        "groupId", node.getArtifact().getGroupId(),
                        "artifactId", node.getArtifact().getArtifactId(),
                        "version", node.getArtifact().getVersion(),
                        "atype", node.getArtifact().getType()));

        /* TODO : on garde ou pas ? devrait être inutile normalement
        if (node.getParent() != null) {
            log.info("parent: " + node.getParent().getArtifact().getArtifactId() + " - " + node.getParent().getArtifact().getVersion() + " - " + node.getParent().getArtifact().getType());

            // TODO : faire un seul statement
            tx.run("MERGE (parent:Artifact {artifactId:$artifactId, version:$version, type:$atype}) \n",
                    Values.parameters(
                            "artifactId", node.getParent().getArtifact().getArtifactId(),
                            "version", node.getParent().getArtifact().getVersion(),
                            "atype", node.getParent().getArtifact().getType()));
            tx.run("MATCH (parent:Artifact {artifactId:$artifactId, version:$version, type:$atype}) \n" +
                    "MATCH (child:Artifact {artifactId:$childArtifactId, version:$childVersion, type:$childType}) \n" +
                    "MERGE (parent)-[:DEPENDS_ON {scope:$scope}]->(child)", Values.parameters(
                    "artifactId", node.getParent().getArtifact().getArtifactId(),
                    "version", node.getParent().getArtifact().getVersion(),
                    "atype", node.getParent().getArtifact().getType(),
                    "childArtifactId", node.getArtifact().getArtifactId(),
                    "childVersion", node.getArtifact().getVersion(),
                    "childType", node.getArtifact().getType(),
                    "scope", "TODO"));
        }*/

        for (DependencyNode child : node.getChildren()) {
            log.info("child: " + child.getArtifact().getArtifactId() + " - " + child.getArtifact().getVersion() + " - " + child.getArtifact().getType());

            tx.run(
                    String.join("\n",
                            "MERGE (childAV:ArtifactAllVersion {groupId:$childGroupId, artifactId:$childArtifactId, type:$childType})",
                            "MERGE (child:Artifact {groupId:$childGroupId, artifactId:$childArtifactId, type:$childType, version:$childVersion})",
                            "MERGE (child) <-[:VERSION]- (childAV)"),
                    Values.parameters(
                            "childGroupId", child.getArtifact().getGroupId(),
                            "childArtifactId", child.getArtifact().getArtifactId(),
                            "childVersion", child.getArtifact().getVersion(),
                            "childType", child.getArtifact().getType()));
            tx.run(String.join("\n",
                    "MATCH (parent:Artifact {groupId:$groupId, artifactId:$artifactId, version:$version, type:$atype})",
                    "MATCH (child:Artifact {groupId:$childGroupId, artifactId:$childArtifactId, version:$childVersion, type:$childType})",
                    "MERGE (parent)-[:DEPENDS_ON {scope : $scope}]->(child)"),
                    Values.parameters(
                        "groupId", node.getArtifact().getGroupId(),
                        "artifactId", node.getArtifact().getArtifactId(),
                        "version", node.getArtifact().getVersion(),
                        "atype", node.getArtifact().getType(),
                        "childGroupId", child.getArtifact().getGroupId(),
                        "childArtifactId", child.getArtifact().getArtifactId(),
                        "childVersion", child.getArtifact().getVersion(),
                        "childType", child.getArtifact().getType(),
                        "scope", child.getArtifact().getScope()
                    ));
        }

        return true;
    }

    @Override
    public boolean endVisit(org.apache.maven.shared.dependency.tree.DependencyNode node) {
        return true;
    }
}
