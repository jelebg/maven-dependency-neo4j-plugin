package org.apache.maven.plugin.dependency.neo4j;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.neo4j.driver.v1.*;

/**
 * Displays the dependency tree for this project.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @version $Id$
 * @since 2.0-alpha-5
 */
@Mojo( name = "clean", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true )
public class Neo4jCleanMojo
    extends AbstractMojo
{
    /**
     * The Maven project.
     */
    @Component
    private MavenProject project;

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

    /*
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {

        AuthToken auth = null;
        if (neo4jPassword != null && neo4jPassword != null) {
            auth = AuthTokens.basic( "neo4j", "root" );
        }

        // TODO : parametres pour uri, user et mdp
        try (Driver driver = GraphDatabase.driver( neo4jUri, auth);
            Session session = driver.session(AccessMode.WRITE)) {
            session.writeTransaction(tx -> {

                tx.run("MATCH (a:Artifact) DETACH DELETE a");// TODO : bulk delete
                tx.run("MATCH (a:ArtifactAllVersion) DETACH DELETE a");// TODO : bulk delete

                return null;
            });
        }

    }


}
