package org.apache.maven.plugin.dependency.neo4j;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilder;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilderException;
import org.neo4j.driver.v1.*;

/**
 * Displays the dependency tree for this project.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 * @since 2.0-alpha-5
 */
@Mojo( name = "neo4j", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true )
public class Neo4jMojo
    extends AbstractMojo
{
    // fields -----------------------------------------------------------------

    /**
     * The Maven project.
     */
    @Component
    private MavenProject project;

    /**
     * The dependency tree builder to use for verbose output.
     */
    @Component
    private DependencyTreeBuilder dependencyTreeBuilder;

    /**
     * The Neo4j Connexion url. By default : bolt://localhost
     *
     * @since 1.0
     */
    @Parameter( property = "neo4jUri", defaultValue = "bolt://localhost")
    private String neo4jUri;

    /**
     * The user for connexion to Neo4J url. By default : neo4j
     *
     * @since 1.0
     */
    @Parameter( property = "neo4jUser", defaultValue = "neo4j")
    private String neo4jUser;

    /**
     * The password for connexion to Neo4J url.
     *
     * @since 1.0
     */
    // TODO : remove default value
    @Parameter( property = "neo4jPassword", defaultValue = "root")
    private String neo4jPassword;


    // TODO : parametres aussi pour login/mdp

    // TODO : delete or use later
    @Parameter( defaultValue = "${localRepository}", readonly = true )
    private ArtifactRepository localRepository;

    /*
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {

        try {

            // TODO : tree builder ou graph builder ?
            org.apache.maven.shared.dependency.tree.DependencyNode root = dependencyTreeBuilder.buildDependencyTree( project, localRepository, null);

            AuthToken auth = null;
            if (neo4jPassword != null && neo4jPassword != null) {
                auth = AuthTokens.basic( "neo4j", "root" );
            }

            try (Driver driver = GraphDatabase.driver(neo4jUri, auth);
                Session session = driver.session(AccessMode.WRITE)) {
                session.writeTransaction(tx -> {

                    root.accept(new Neo4jDependencyNodeVisitor(tx, getLog()));

                    Artifact parent = project.getParentArtifact();
                    Artifact artifact = project.getArtifact();

                    if (artifact != null && parent != null) {
                        tx.run("MERGE (parent:Artifact {artifactId:$artifactId, version:$version, type:$atype}) \n",
                                Values.parameters(
                                        "artifactId", parent.getArtifactId(),
                                        "version", parent.getVersion(),
                                        "atype", parent.getType()));
                        tx.run("MATCH (parent:Artifact {artifactId:$artifactId, version:$version, type:$atype}) \n" +
                                "MATCH (child:Artifact {artifactId:$childArtifactId, version:$childVersion, type:$childType}) \n" +
                                "MERGE (parent)-[:PARENT_OF]->(child)", Values.parameters(
                                "artifactId", parent.getArtifactId(),
                                "version", parent.getVersion(),
                                "atype", parent.getType(),
                                "childArtifactId", artifact.getArtifactId(),
                                "childVersion", artifact.getVersion(),
                                "childType", artifact.getType()));
                    }

                    return null;
                });
            }


        } catch (DependencyTreeBuilderException e) {
            getLog().error("exception pendant la construction du graph de dépendances", e);
        }
    }


}
