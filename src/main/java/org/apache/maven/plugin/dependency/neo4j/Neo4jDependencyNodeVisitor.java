package org.apache.maven.plugin.dependency.neo4j;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.shared.dependency.tree.DependencyNode;
import org.apache.maven.shared.dependency.tree.traversal.DependencyNodeVisitor;
import org.neo4j.driver.v1.Session;
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

    private Session session;
    private Log log;
    Transaction currentTx;
    int writeCounter = 0;

    private static final int MAX_WRITE_PER_TRANSACTION = 1000;

    /**
     * Constructor.
     *
     * @ param writer the writer to write to.
     */
    public Neo4jDependencyNodeVisitor(Session session, Log log)
    {
        this.session = session;
        this.log = log;
    }

    @Override
    public boolean visit(org.apache.maven.shared.dependency.tree.DependencyNode node) {
        if (this.currentTx == null) {
            this.currentTx = session.beginTransaction();
        }

        currentTx.run(
                String.join("\n",
                        "MERGE (parentAV:ArtifactAllVersion {groupId:$groupId, artifactId:$artifactId, type:$atype})",
                        "MERGE (parent:Artifact {groupId:$groupId, artifactId:$artifactId, type:$atype, version:$version})",
                        "MERGE (parent) <-[:VERSION]- (parentAV)"),
                Values.parameters(
                        "groupId", node.getArtifact().getGroupId(),
                        "artifactId", node.getArtifact().getArtifactId(),
                        "version", node.getArtifact().getVersion(),
                        "atype", node.getArtifact().getType()));
        writeCounter ++;

        /* TODO : on garde ou pas ? devrait être inutile normalement
        if (node.getParent() != null) {
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
            currentTx.run(
                    String.join("\n",
                            "MERGE (childAV:ArtifactAllVersion {groupId:$childGroupId, artifactId:$childArtifactId, type:$childType})",
                            "MERGE (child:Artifact {groupId:$childGroupId, artifactId:$childArtifactId, type:$childType, version:$childVersion})",
                            "MERGE (child) <-[:VERSION]- (childAV)"),
                    Values.parameters(
                            "childGroupId", child.getArtifact().getGroupId(),
                            "childArtifactId", child.getArtifact().getArtifactId(),
                            "childVersion", child.getArtifact().getVersion(),
                            "childType", child.getArtifact().getType()));
            writeCounter ++;

            currentTx.run(String.join("\n",
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
            writeCounter ++;
        }

        if (writeCounter > MAX_WRITE_PER_TRANSACTION) {
            writeCounter = 0;
            currentTx.success();
            currentTx.close();
            currentTx = null;
        }

        return true;
    }

    @Override
    public boolean endVisit(org.apache.maven.shared.dependency.tree.DependencyNode node) {
        return true;
    }

    public void endVisit() {
        log.info("endVisit");
        if (currentTx != null) {
            log.info("endVisit commitAsync");
            writeCounter = 0;
            currentTx.success();
            currentTx.close();
            currentTx = null;
        }
    }
}
