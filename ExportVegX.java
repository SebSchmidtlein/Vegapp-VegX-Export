package edu.kit.ifgg.vegapp.common;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import androidx.documentfile.provider.DocumentFile;
import android.util.Log;
import android.util.Xml;
import android.widget.Toast;

import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.kit.ifgg.vegapp.activities.MainActivity;
import edu.kit.ifgg.vegapp.models.Layer;
import edu.kit.ifgg.vegapp.models.Method;
import edu.kit.ifgg.vegapp.models.Party;
import edu.kit.ifgg.vegapp.BuildConfig;
import edu.kit.ifgg.vegapp.R;


/**
 * Created by Dennis on 12.12.2016.
 */

public class ExportVegX {
    private DBhelper dbh;
    private SQLiteDatabase db;
    private final String projectName, projectId, coverScale, layersystemID;
    private int profileId;
    private Map<String, List<String>> plotObservatorIds;
    private Map<String, String> spatialReferenceLookup;
    private Map<CoordSpatialPair, String> coordinateUnitsLookup;
    private Set<String> dbhAboveGroundLookup;
    private Set<String> girthAboveGroundLookup;
    private Map<String, String> polarCoordinateDirectionLookup;
    private Map<String, String> polarCoordinateDistanceLookup;
    private Set<QuarterProtocolPair> individualLocationQuarterProtocolSet;
    private Map<String, String> individualLocationQuarterLookup;

    private String nominal = "nominal";
    private String ordinal = "ordinal";
    private String interval_ratio = "interval/ratio";
    private String surface_cover = "Surface cover";
    private String location = "Location";
    private String plot_geometry = "Plot geometry";
    private String placement_strategy = "Placement strategy";
    private String meta_data = "Meta data for the observation";
    private String legal = "Legal";
    private String literature_source = "Literature source";
    private String community_observation = "Community observations";
    private String site_observation = "Site observations";
    private String legal_status = "Legal status, land use and management";
    private String custom = "Custom";
    private String scope_plots = "plots";
    private String scope_species = "species";
    private String scope_individuals = "individuals";

    private ArrayList<Party> parties = new ArrayList<Party>();
    private ArrayList<Party> partiesAll = new ArrayList<Party>();
    private ArrayList<Party> dataOwnersAll = new ArrayList<Party>();

    private ArrayList<Method> methods = new ArrayList<Method>();
    private ArrayList<Method> methodsAll = new ArrayList<Method>();

    public static final String encoding = "ISO-8859-1";

    private class CoordSpatialPair {
        private String coordinateUnits;
        private String spatialReference;

        public CoordSpatialPair(String coordinateUnits, String spatialReference) {
            this.coordinateUnits = coordinateUnits;
            this.spatialReference = spatialReference;
        }

        public String getCoordinateUnits() {
            return coordinateUnits;
        }

        public String getSpatialReference() {
            return spatialReference;
        }
    }

    private class QuarterProtocolPair {
        private String quarter;
        private String protocol;

        public QuarterProtocolPair(String quarter, String protocol) {
            this.quarter = quarter;
            this.protocol = protocol;
        }

        public String getQuarter() {
            return quarter;
        }

        public String getProtocol() {
            return protocol;
        }
    }

    // >=API30
    public ExportVegX(Context context, Uri uri) {

        // Attributes are also written when Default value is set !!

        //open db
        dbh = DBhelper.getInstance(context);
        db = dbh.getWritableDatabase();

        //create new file, create serializer

        Cursor projectCursor = db.rawQuery("Select * FROM project WHERE _id =  \"" + MainActivity.exportedProjectId + "\"", null);
        try {
            projectCursor.moveToFirst();
            projectName = projectCursor.getString(projectCursor.getColumnIndex("name"));
            projectId = projectCursor.getString(projectCursor.getColumnIndex("_id"));
            coverScale = projectCursor.getString(projectCursor.getColumnIndex("cover_scale_id"));
            profileId = projectCursor.getInt(projectCursor.getColumnIndex("profile_id"));
            layersystemID = projectCursor.getString(projectCursor.getColumnIndex("layer_system_id"));
        } finally {
            projectCursor.close();
        }

        XmlSerializer serializer = Xml.newSerializer();

        plotObservatorIds = new HashMap<String, List<String>>();
        spatialReferenceLookup = new HashMap<>();
        coordinateUnitsLookup = new HashMap<>();
        dbhAboveGroundLookup = new HashSet<>();
        girthAboveGroundLookup = new HashSet<>();
        polarCoordinateDirectionLookup = new HashMap<>();
        polarCoordinateDistanceLookup = new HashMap<>();
        individualLocationQuarterProtocolSet = new HashSet<>();
        individualLocationQuarterLookup = new HashMap<>();

        //start file
        try {
            ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "w");

            OutputStream fileos = new FileOutputStream(pfd.getFileDescriptor());

            //start writing
            try {

                serializer.setOutput(fileos, encoding);
                serializer.startDocument(null, true);
                serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);

                // start vegx root class
                serializer.setPrefix("xsi", "http://www.w3.org/2001/XMLSchema-instance");
                serializer.setPrefix("dwg", "http://rs.tdwg.org/dwc/geospatial/");
                serializer.setPrefix("tcs", "http://www.tdwg.org/schemas/tcs/1.01");
                serializer.startTag(null, "vegX");
                // serializer.attribute("http://www.w3.org/2001/XMLSchema-instance", "noNamespaceSchemaLocation", "F:\\project\\vegX\\VegX_Schema_1.5.1\\veg.xsd");  // for easier testing, remove later

                // write a veg.xsd <projects><project /></projects> entry
                writeParties(serializer);
                writeLiteratureCitations(serializer);
                serializer.startTag(null , "methods");
                writeMethods(serializer); //TODO: are all of these always written?
                serializer.endTag(null , "methods");
                writeAttributes(serializer); //TODO: are all of these always written?
                writeStrata(serializer);
                writeSurfaceTypes(serializer);
                writeOrganismNames(serializer);
                writeTaxonConcepts(serializer);
                writeOrganismIdentities(serializer);
                writeCommunityConcepts(serializer);
                writeCommunityDeterminations(serializer);
                writeProject(serializer);
                writePlots(serializer);
                writeIndividualOrganisms(serializer);
                writePlotObversations(serializer);
                writeIndividualOrganismObservations(serializer);
                writeAggregateOrganismObservations(serializer);
                writeStratumObversations(serializer);
                writeCommunityObversations(serializer);
                writeSurfaceCoverObservations(serializer);
                writeSiteObversations(serializer);
                // notes
                Calendar calendar = Calendar.getInstance();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                String exportDate = dateFormat.format(calendar.getTime());

                serializer.startTag(null, "note");
                serializer.startTag(null, "text");
                serializer.text("Data collection and export were done using Vegapp for Android " + BuildConfig.VERSION_CODE + ".");
                serializer.endTag(null, "text");
                serializer.startTag(null, "date");
                serializer.text(exportDate);
                serializer.endTag(null, "date");
                serializer.endTag(null, "note");

                serializer.startTag(null, "note");
                serializer.startTag(null, "text");
                String nameSpeciesList = SharedPrefs.getString(MainActivity.mContext, "species_lu_version");
                String termsSpeciesList = SharedPrefs.getString(MainActivity.mContext, "species_terms");

                String noteText = "Species reference list used while exporting this data: " + nameSpeciesList + ". " + termsSpeciesList;
                noteText = noteText.replaceAll("(.{100})", "$1\n");

                serializer.text(noteText);
                serializer.endTag(null, "text");
                serializer.startTag(null, "date");
                serializer.text(exportDate);
                serializer.endTag(null, "date");
                serializer.endTag(null, "note");

                // end vegx root class
                serializer.endTag(null, "vegX");

            } catch (Exception e) {
                Toast toast = Toast.makeText(context, R.string.export_project_vegx_toast_a, Toast.LENGTH_SHORT);
                toast.show();
                Log.e("Exception", "Could not write file. " + e);
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                Log.e("Stacktrace", sw.toString());
            }

            //end file
            serializer.flush();
            final PrintStream printStream = new PrintStream(fileos);
            printStream.print("\n");
            printStream.close();
            fileos.flush();

            serializer.endDocument();
            fileos.close();
            pfd.close();

            String filename = "";

            filename = DocumentFile.fromSingleUri(context, uri).getName();

            Toast toast = Toast.makeText(context, context.getString(R.string.export_project_vegx_toast_b) + "/Documents/" + filename, Toast.LENGTH_SHORT);
            toast.show();
            db.close();
            dbh.close();
        } catch (IOException e) {
            Toast toast = Toast.makeText(MainActivity.mContext, R.string.export_project_vegx_toast_c, Toast.LENGTH_SHORT);
            toast.show();
            Log.e("IOException", "Could not create file. " + e);
        }

    }

    // < API 30
    public ExportVegX(Context context, String path) {
        File newxmlfile;
        String filepath;
        //open db
        dbh = DBhelper.getInstance(context);
        db = dbh.getWritableDatabase();

        //create new file, create serializer

        Cursor projectCursor = db.rawQuery("Select * FROM project WHERE _id =  \"" + MainActivity.exportedProjectId + "\"", null);
        try {
            projectCursor.moveToFirst();
            projectName = projectCursor.getString(projectCursor.getColumnIndex("name"));
            projectId = projectCursor.getString(projectCursor.getColumnIndex("_id"));
            coverScale = projectCursor.getString(projectCursor.getColumnIndex("cover_scale_id"));
            profileId = projectCursor.getInt(projectCursor.getColumnIndex("profile_id"));
            layersystemID = projectCursor.getString(projectCursor.getColumnIndex("layer_system_id"));
            filepath = path + ".xml";
        } finally {
            projectCursor.close();
        }

        newxmlfile = new File(filepath);
        XmlSerializer serializer = Xml.newSerializer();

        plotObservatorIds = new HashMap<String, List<String>>();
        spatialReferenceLookup = new HashMap<>();
        coordinateUnitsLookup = new HashMap<>();
        dbhAboveGroundLookup = new HashSet<>();
        girthAboveGroundLookup = new HashSet<>();
        polarCoordinateDirectionLookup = new HashMap<>();
        polarCoordinateDistanceLookup = new HashMap<>();
        individualLocationQuarterProtocolSet = new HashSet<>();
        individualLocationQuarterLookup = new HashMap<>();

        //start file
        try {
            newxmlfile.createNewFile();
            OutputStream fileos = new FileOutputStream(newxmlfile);

            //start writing
            try {

                serializer.setOutput(fileos, encoding);
                serializer.startDocument(null, true);
                serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);

                // start vegx root class
                serializer.setPrefix("xsi", "http://www.w3.org/2001/XMLSchema-instance");
                serializer.setPrefix("dwg", "http://rs.tdwg.org/dwc/geospatial/");
                serializer.setPrefix("tcs", "http://www.tdwg.org/schemas/tcs/1.01");
                serializer.startTag(null, "vegX");
                // serializer.attribute("http://www.w3.org/2001/XMLSchema-instance", "noNamespaceSchemaLocation", "F:\\project\\vegX\\VegX_Schema_1.5.1\\veg.xsd");  // for easier testing, remove later

                // write a veg.xsd <projects><project /></projects> entry
                writeParties(serializer);
                writeLiteratureCitations(serializer);
                serializer.startTag(null , "methods");
                writeMethods(serializer); //TODO: are all of these always written?
                serializer.endTag(null , "methods");
                writeAttributes(serializer); //TODO: are all of these always written?
                writeStrata(serializer);
                writeSurfaceTypes(serializer);
                writeOrganismNames(serializer);
                writeTaxonConcepts(serializer);
                writeOrganismIdentities(serializer);
                writeCommunityConcepts(serializer);
                writeCommunityDeterminations(serializer);
                writeProject(serializer);
                writePlots(serializer);
                writeIndividualOrganisms(serializer);
                writePlotObversations(serializer);
                writeIndividualOrganismObservations(serializer);
                writeAggregateOrganismObservations(serializer);
                writeStratumObversations(serializer);
                writeCommunityObversations(serializer);
                writeSurfaceCoverObservations(serializer);
                writeSiteObversations(serializer);
                // notes
                Calendar calendar = Calendar.getInstance();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                String exportDate = dateFormat.format(calendar.getTime());

                serializer.startTag(null, "note");
                serializer.startTag(null, "text");
                serializer.text("Data collection and export were done using the VegApp for Android " + BuildConfig.VERSION_CODE + ".");
                serializer.endTag(null, "text");
                serializer.startTag(null, "date");
                serializer.text(exportDate);
                serializer.endTag(null, "date");
                serializer.endTag(null, "note");

                serializer.startTag(null, "note");
                serializer.startTag(null, "text");
                String nameSpeciesList = SharedPrefs.getString(MainActivity.mContext, "species_lu_version");
                String termsSpeciesList = SharedPrefs.getString(MainActivity.mContext, "species_terms");

                String noteText = "Species reference list used while exporting this data: " + nameSpeciesList + ". " + termsSpeciesList;
                noteText = noteText.replaceAll("(.{100})", "$1\n");

                serializer.text(noteText);
                serializer.endTag(null, "text");
                serializer.startTag(null, "date");
                serializer.text(exportDate);
                serializer.endTag(null, "date");
                serializer.endTag(null, "note");

                // end vegx root class
                serializer.endTag(null, "vegX");

            } catch (Exception e) {
                Toast toast = Toast.makeText(context, R.string.export_project_vegx_toast_a, Toast.LENGTH_SHORT);
                toast.show();
                Log.e("Exception", "Could not write file. " + e);
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                Log.e("Stacktrace", sw.toString());
            }

            //end file
            serializer.flush();
            final PrintStream printStream = new PrintStream(fileos);
            printStream.print("\n");
            printStream.close();
            fileos.flush();

            serializer.endDocument();
            fileos.close();

            ScanStorage.scan(newxmlfile, context);

            Toast toast = Toast.makeText(context, context.getString(R.string.export_project_vegx_toast_b) + filepath, Toast.LENGTH_SHORT);
            toast.show();
            db.close();
            dbh.close();
        } catch (IOException e) {
            Toast toast = Toast.makeText(context, R.string.export_project_vegx_toast_c, Toast.LENGTH_SHORT);
            toast.show();
            Log.e("IOException", "Could not create file. " + e);
        }

    }


    //************************************************************//
    //********************** parties  ****************************//
    //************************************************************//

    private void writeParties(XmlSerializer serializer) throws IOException {

        String _id, givenname, surname, organization, plot, originatorId;

        // party A Originators / observers
        Cursor partyObserverCursor = db.rawQuery("Select _id, givenname, surname, plot FROM observer WHERE project = \"" + MainActivity.exportedProjectId + "\"", null);
        try {

            Cursor partyOwnerCursor = db.rawQuery("Select _id, owner_givenname, owner_surname, owner_organization FROM plot WHERE project_id is " + projectId, null);
            try {
                // this cursor should have exactly one entry for any given profile_id.
                if (partyOwnerCursor.moveToNext()) {
                    String POgivenname = partyOwnerCursor.getString(partyOwnerCursor.getColumnIndex("owner_givenname"));
                    String POsurname = partyOwnerCursor.getString(partyOwnerCursor.getColumnIndex("owner_surname"));
                    organization = partyOwnerCursor.getString(partyOwnerCursor.getColumnIndex("owner_organization"));
                    String plotId = partyOwnerCursor.getString(partyOwnerCursor.getColumnIndex("_id"));

                    boolean writePartiesTag = !(POgivenname != null && POgivenname.isEmpty() && POsurname != null && POsurname.isEmpty() && organization != null && organization.isEmpty() &&
                            partyObserverCursor.getCount() == 0);

                    if (writePartiesTag) {

                        serializer.startTag(null, "parties");

                        while (partyObserverCursor.moveToNext()) {
                            _id = partyObserverCursor.getString(partyObserverCursor.getColumnIndex("_id"));
                            givenname = partyObserverCursor.getString(partyObserverCursor.getColumnIndex("givenname"));
                            surname = partyObserverCursor.getString(partyObserverCursor.getColumnIndex("surname"));
                            plot = partyObserverCursor.getString(partyObserverCursor.getColumnIndex("plot"));
                            originatorId = "originator_" + _id;

                            // map originatorId to plots for later reference
                            if (!plotObservatorIds.containsKey(plot)) {
                                // no key, create new list
                                plotObservatorIds.put(plot, new ArrayList<String>());
                            }
                            plotObservatorIds.get(plot).add(originatorId);
                            String partyObserverName = "";
                            if (givenname != null) {
                                partyObserverName += givenname;
                            }
                            if (surname != null) {
                                partyObserverName += ", " + surname;
                            }
                            if (!givenname.isEmpty() || !surname.isEmpty()) {
                                Party party = new Party(originatorId, partyObserverName);
                                partiesAll.add(party);

                                if (!checkInParties(party)) {


                                    serializer.startTag(null, "party");
                                    serializer.attribute(null, "id", originatorId);
                                    serializer.startTag(null, "individualName");
                                    serializer.text(partyObserverName);
                                    serializer.endTag(null, "individualName");
                                    serializer.endTag(null, "party");


                                    parties.add(new Party(originatorId, partyObserverName));
                                }
                            }
                        }
                            String partyOwnerName = "";
                            if (POgivenname != null) {
                                partyOwnerName += POgivenname;
                            }
                            if (POsurname != null) {
                                partyOwnerName += ", " + POsurname;
                            }
                            if ( (POgivenname != null && !POgivenname.isEmpty()) || (POsurname != null && !POsurname.isEmpty())) {

                                Party party = new Party(String.valueOf(profileId), partyOwnerName);
                                dataOwnersAll.add(party);

                                serializer.startTag(null, "party");
                                serializer.attribute(null, "id", "owner_" + String.valueOf(profileId));
                                if (!partyOwnerName.isEmpty()) {
                                    serializer.startTag(null, "individualName");
                                    serializer.text(partyOwnerName);
                                    serializer.endTag(null, "individualName");
                                } else {
                                    serializer.startTag(null, "organizationName");
                                    if (organization != null) {
                                        serializer.text(organization);
                                    }
                                    serializer.endTag(null, "organizationName");
                                }
                                serializer.endTag(null, "party");
                            }

                        serializer.endTag(null, "parties");
                    }
                }

            } finally {
                partyOwnerCursor.close();
            }

        } finally {
            partyObserverCursor.close();
        }

    }

    private boolean checkInParties(Party party) {
        for (Party p : parties) {
            if (p.getIndividual_name().equals(party.getIndividual_name())) {
                return true;
            }
        }
        return false;
    }

    private String getOriginatorIDFromParties(String id) {
        String name = "";
        String id_ret = id;
        for (Party p : partiesAll) {
            if (p.getId().equals(id)) {
                name = p.getIndividual_name();
            }
        }
        for (Party p : parties) {
            if (p.getIndividual_name().equals(name)) {
                id_ret = p.getId();
            }
        }
        return id_ret;
    }

    private boolean getOriginatorIDFromDataOwner(String profile_id) {
        for (Party p : dataOwnersAll) {
            if (p.getId().equals(profile_id)) {
                return true;
            }
        }
        return false;
    }

    //************************************************************//
    //**************** literatureCitations ***********************//
    //************************************************************//

    private void writeLiteratureCitations(XmlSerializer serializer) throws IOException {
        //cursors
        Cursor plotCursor = db.rawQuery("Select * FROM plot WHERE project_id =  \"" + MainActivity.exportedProjectId + "\"", null);
        String _id, source_reference;
        boolean started = false;
    
        //plot loop
        try {
            while (plotCursor.moveToNext()) {
                _id = plotCursor.getString(plotCursor.getColumnIndex("_id"));
                source_reference = plotCursor.getString(plotCursor.getColumnIndex("source_reference"));
                
                // Check if there is a source_reference before writing the node
                if (source_reference != null && !source_reference.isEmpty()) {
                    if (!started) {
                        serializer.startTag(null, "literatureCitations");
                        started = true;
                    }
                    serializer.startTag(null, "literatureCitation");
                    serializer.attribute(null, "id", "citation_" + _id);
                    serializer.startTag(null, "citationString");
                    serializer.text(source_reference);
                    serializer.endTag(null, "citationString");
                    serializer.endTag(null, "literatureCitation");
                }
    
                Cursor speciesCursor = db.rawQuery("Select * FROM species WHERE plot_id =  \"" + _id + "\"", null);
                try {
                    while (speciesCursor.moveToNext()) {
                        String speciesId = speciesCursor.getString(speciesCursor.getColumnIndex("_id"));
                        String citation = speciesCursor.getString(speciesCursor.getColumnIndex("taxon_concept"));
                        
                        // Check if there is a citation before writing the node
                        if (speciesId != null && !speciesId.isEmpty() && citation != null && !citation.isEmpty()) {
                            if (!started) {
                                serializer.startTag(null, "literatureCitations");
                                started = true;
                            }
                            serializer.startTag(null, "literatureCitation");
                            serializer.attribute(null, "id", "taxonConcept_citation_species_" + speciesId);
                            serializer.startTag(null, "citationString");
                            serializer.text(citation);
                            serializer.endTag(null, "citationString");
                            serializer.endTag(null, "literatureCitation");
                        }
                    }
                } finally {
                    speciesCursor.close();
                }
    
                Cursor individualCursor = db.rawQuery("Select * FROM individuals WHERE plot_id =  \"" + _id + "\"", null);
                try {
                    while (individualCursor.moveToNext()) {
                        String individualId = individualCursor.getString(individualCursor.getColumnIndex("_id"));
                        String citation = individualCursor.getString(individualCursor.getColumnIndex("taxon_concept"));
                        
                        // Check if there is a citation before writing the node
                        if (individualId != null && !individualId.isEmpty() && citation != null && !citation.isEmpty()) {
                            if (!started) {
                                serializer.startTag(null, "literatureCitations");
                                started = true;
                            }
                            serializer.startTag(null, "literatureCitation");
                            serializer.attribute(null, "id", "taxonConcept_citation_individual_" + individualId);
                            serializer.startTag(null, "citationString");
                            serializer.text(citation);
                            serializer.endTag(null, "citationString");
                            serializer.endTag(null, "literatureCitation");
                        }
                    }
                } finally {
                    individualCursor.close();
                }
            }
        } finally {
            plotCursor.close();
        }
        if (started) {
            serializer.endTag(null, "literatureCitations");
        }
    }

    //************************************************************//
    //********************** methods  ****************************//
    //************************************************************//
    private void writeMethods(XmlSerializer serializer) throws IOException {


        Cursor layerSystemsCursor = db.rawQuery("SELECT * FROM layer_system WHERE _id = " + layersystemID, null);
        layerSystemsCursor.moveToFirst();
        String layer_name = layerSystemsCursor.getString(layerSystemsCursor.getColumnIndex("name"));
        if (layer_name.equals("Default")) {
            /* DONE: don't always just write default layer -> create a new method for custom_layers */
            // default layer
            serializer.startTag(null, "method");
            serializer.attribute(null, "id", "defaultLayerSystem");
            serializer.startTag(null, "name");
            serializer.text("VegApp - Default Layer Categories");
            serializer.endTag(null, "name");
            serializer.startTag(null, "description");
            serializer.text("Layer classification using mixed criteria (height and life form)");
            serializer.endTag(null, "description");
            serializer.startTag(null, "subject");
            serializer.text("Classification of vegetation layers (strata, tiers)");
            serializer.endTag(null, "subject");
            serializer.endTag(null, "method");
        } else {
            // custom layer
            serializer.startTag(null, "method");
            serializer.attribute(null, "id", "customLayerSystem");
            serializer.startTag(null, "name");
            serializer.text(layer_name);
            serializer.endTag(null, "name");
            serializer.startTag(null, "description");
            try {
                serializer.text(layerSystemsCursor.getString(layerSystemsCursor.getColumnIndex("layer_system_description")));
            } catch (NullPointerException e) {
                e.printStackTrace();
                serializer.text("");
            }
            ;
            serializer.endTag(null, "description");
            serializer.startTag(null, "subject");
            serializer.text("Classification of vegetation layers (strata, tiers)");
            serializer.endTag(null, "subject");
            serializer.endTag(null, "method");
        }
        layerSystemsCursor.close();

        // method_location_accuracy_in_meter
        serializer.startTag(null, "method");
        serializer.attribute(null, "id", "method_location_accuracy_in_meter");
        serializer.startTag(null, "name");
        serializer.text("Location accuracy in meters");
        serializer.endTag(null, "name");
        serializer.startTag(null, "description");
        serializer.endTag(null, "description");
        serializer.startTag(null, "subject");
        serializer.text("Location accuracy");
        serializer.endTag(null, "subject");
        serializer.startTag(null, "citationString");
        serializer.endTag(null, "citationString");
        serializer.endTag(null, "method");

        // method_plot_radius_in_meter
        serializer.startTag(null, "method");
        serializer.attribute(null, "id", "method_plot_radius_in_meter");
        serializer.startTag(null, "name");
        serializer.text("Plot radius in meters");
        serializer.endTag(null, "name");
        serializer.startTag(null, "description");
        serializer.endTag(null, "description");
        serializer.startTag(null, "subject");
        serializer.text("Plot radius");
        serializer.endTag(null, "subject");
        serializer.startTag(null, "citationString");
        serializer.endTag(null, "citationString");
        serializer.endTag(null, "method");

        // method_plot_width_in_meter
        serializer.startTag(null, "method");
        serializer.attribute(null, "id", "method_plot_width_in_meter");
        serializer.startTag(null, "name");
        serializer.text("Plot width in meters");
        serializer.endTag(null, "name");
        serializer.startTag(null, "description");
        serializer.endTag(null, "description");
        serializer.startTag(null, "subject");
        serializer.text("Plot width");
        serializer.endTag(null, "subject");
        serializer.startTag(null, "citationString");
        serializer.endTag(null, "citationString");
        serializer.endTag(null, "method");

        // method_plot_length_in_meter
        serializer.startTag(null, "method");
        serializer.attribute(null, "id", "method_plot_length_in_meter");
        serializer.startTag(null, "name");
        serializer.text("Plot length in meters");
        serializer.endTag(null, "name");
        serializer.startTag(null, "description");
        serializer.endTag(null, "description");
        serializer.startTag(null, "subject");
        serializer.text("Plot length");
        serializer.endTag(null, "subject");
        serializer.startTag(null, "citationString");
        serializer.endTag(null, "citationString");
        serializer.endTag(null, "method");

        // method_maximum_height_of_layer_in_meter
        serializer.startTag(null, "method");
        serializer.attribute(null, "id", "method_maximum_height_of_layer_in_meter");
        serializer.startTag(null, "name");
        serializer.text("Maximum height of layer in meters");
        serializer.endTag(null, "name");
        serializer.startTag(null, "description");
        serializer.endTag(null, "description");
        serializer.startTag(null, "subject");
        serializer.text("Maximum height of layer");
        serializer.endTag(null, "subject");
        serializer.startTag(null, "citationString");
        serializer.endTag(null, "citationString");
        serializer.endTag(null, "method");

        // method_average_stratum_height_in_meter
        serializer.startTag(null, "method");
        serializer.attribute(null, "id", "method_average_stratum_height_in_meter");
        serializer.startTag(null, "name");
        serializer.text("Average stratum height in meters");
        serializer.endTag(null, "name");
        serializer.startTag(null, "description");
        serializer.endTag(null, "description");
        serializer.startTag(null, "subject");
        serializer.text("Average stratum height");
        serializer.endTag(null, "subject");
        serializer.startTag(null, "citationString");
        serializer.endTag(null, "citationString");
        serializer.endTag(null, "method");

        // method_elevation_above_sea_level_in_meter
        serializer.startTag(null, "method");
        serializer.attribute(null, "id", "method_elevation_above_sea_level_in_meter");
        serializer.startTag(null, "name");
        serializer.text("Elevation above sea level in meters");
        serializer.endTag(null, "name");
        serializer.startTag(null, "description");
        serializer.endTag(null, "description");
        serializer.startTag(null, "subject");
        serializer.text("Elevation above sea level");
        serializer.endTag(null, "subject");
        serializer.startTag(null, "citationString");
        serializer.endTag(null, "citationString");
        serializer.endTag(null, "method");

        // method_location_accuracy_in_dop
        serializer.startTag(null, "method");
        serializer.attribute(null, "id", "method_location_accuracy_in_dop");
        serializer.startTag(null, "name");
        serializer.text("Location accuracy in DOP");
        serializer.endTag(null, "name");
        serializer.startTag(null, "description");
        serializer.endTag(null, "description");
        serializer.startTag(null, "subject");
        serializer.text("Location accuracy");
        serializer.endTag(null, "subject");
        serializer.startTag(null, "citationString");
        serializer.endTag(null, "citationString");
        serializer.endTag(null, "method");

        // method_plot_surface_in_square_meter
        serializer.startTag(null, "method");
        serializer.attribute(null, "id", "method_plot_surface_in_square_meter");
        serializer.startTag(null, "name");
        serializer.text("Plot surface area in square meters");
        serializer.endTag(null, "name");
        serializer.startTag(null, "description");
        serializer.endTag(null, "description");
        serializer.startTag(null, "subject");
        serializer.text("Plot surface area");
        serializer.endTag(null, "subject");
        serializer.startTag(null, "citationString");
        serializer.endTag(null, "citationString");
        serializer.endTag(null, "method");

        // method_plot_orientation_in_degrees
        serializer.startTag(null, "method");
        serializer.attribute(null, "id", "method_plot_orientation_in_degrees");
        serializer.startTag(null, "name");
        serializer.text("Plot orientation in degrees");
        serializer.endTag(null, "name");
        serializer.startTag(null, "description");
        serializer.endTag(null, "description");
        serializer.startTag(null, "subject");
        serializer.text("Plot orientation");
        serializer.endTag(null, "subject");
        serializer.startTag(null, "citationString");
        serializer.endTag(null, "citationString");
        serializer.endTag(null, "method");

        // method_aspect_in_degrees
        serializer.startTag(null, "method");
        serializer.attribute(null, "id", "method_aspect_in_degrees");
        serializer.startTag(null, "name");
        serializer.text("Aspect in degrees");
        serializer.endTag(null, "name");
        serializer.startTag(null, "description");
        serializer.endTag(null, "description");
        serializer.startTag(null, "subject");
        serializer.text("Aspect");
        serializer.endTag(null, "subject");
        serializer.startTag(null, "citationString");
        serializer.endTag(null, "citationString");
        serializer.endTag(null, "method");

        // method_slope_in_degrees
        serializer.startTag(null, "method");
        serializer.attribute(null, "id", "method_slope_in_degrees");
        serializer.startTag(null, "name");
        serializer.text("Slope in degrees");
        serializer.endTag(null, "name");
        serializer.startTag(null, "description");
        serializer.endTag(null, "description");
        serializer.startTag(null, "subject");
        serializer.text("Slope");
        serializer.endTag(null, "subject");
        serializer.startTag(null, "citationString");
        serializer.endTag(null, "citationString");
        serializer.endTag(null, "method");

        // method_percent_cover_of_species
        serializer.startTag(null, "method");
        serializer.attribute(null, "id", "method_percent_cover_of_species");
        serializer.startTag(null, "name");
        serializer.text("Percent cover of species");
        serializer.endTag(null, "name");
        serializer.startTag(null, "description");
        serializer.text("Projected cover");
        serializer.endTag(null, "description");
        serializer.startTag(null, "subject");
        serializer.text("Species cover");
        serializer.endTag(null, "subject");
        serializer.startTag(null, "citationString");
        serializer.endTag(null, "citationString");
        serializer.endTag(null, "method");

        // method_percent_cover_of_surface_material
        serializer.startTag(null, "method");
        serializer.attribute(null, "id", "method_percent_cover_of_surface_material");
        serializer.startTag(null, "name");
        serializer.text("Percent cover of surface material");
        serializer.endTag(null, "name");
        serializer.startTag(null, "description");
        serializer.text("Projected cover");
        serializer.endTag(null, "description");
        serializer.startTag(null, "subject");
        serializer.text("Cover of surface material");
        serializer.endTag(null, "subject");
        serializer.startTag(null, "citationString");
        serializer.endTag(null, "citationString");
        serializer.endTag(null, "method");

        // method_percent_cover_of_layer_stratum
        serializer.startTag(null, "method");
        serializer.attribute(null, "id", "method_percent_cover_of_layer_stratum");
        serializer.startTag(null, "name");
        serializer.text("Percent cover of layer (stratum)");
        serializer.endTag(null, "name");
        serializer.startTag(null, "description");
        serializer.text("Projected cover");
        serializer.endTag(null, "description");
        serializer.startTag(null, "subject");
        serializer.text("Cover of layer (stratum)");
        serializer.endTag(null, "subject");
        serializer.startTag(null, "citationString");
        serializer.endTag(null, "citationString");
        serializer.endTag(null, "method");
        try {
            if (coverScale != null && !coverScale.equals("'00'") && !coverScale.equals("") ) {
                // coverscale
                serializer.startTag(null, "method");
                serializer.attribute(null, "id", "method_coverscale");
                // substring to cut out the leading and trailing apostroph in project column entry
                Cursor cover_scale_name_cursor = db.rawQuery("select names from cover_scale_lu where codes = \"" + (coverScale.substring(1, coverScale.length() - 1)) + "\"", null);
                cover_scale_name_cursor.moveToFirst();
                String cover_scale_name = cover_scale_name_cursor.getString(cover_scale_name_cursor.getColumnIndex("names"));
                cover_scale_name_cursor.close();
                serializer.startTag(null, "name");
                if (cover_scale_name != null) {
                    serializer.text(cover_scale_name);
                }
                serializer.endTag(null, "name");
                serializer.startTag(null, "description");
                serializer.text("Ordinal cover scale");
                serializer.endTag(null, "description");
                serializer.startTag(null, "subject");
                serializer.text("Cover scale");
                serializer.endTag(null, "subject");
                serializer.startTag(null, "citationString");
                serializer.endTag(null, "citationString");
                serializer.endTag(null, "method");
            }
        } catch (Exception e) {
            Log.i("vegx", e.toString());
        }

        // method_geographic_latitude_longitude_in_decimal_degrees
        serializer.startTag(null, "method");
        serializer.attribute(null, "id", "method_geographic_latitude_longitude_in_decimal_degrees");
        serializer.startTag(null, "name");
        serializer.text("Geographic latitude and longitude");
        serializer.endTag(null, "name");
        serializer.startTag(null, "description");
        serializer.endTag(null, "description");
        serializer.startTag(null, "subject");
        serializer.text("Coordinate system");
        serializer.endTag(null, "subject");
        serializer.startTag(null, "citationString");
        serializer.endTag(null, "citationString");
        serializer.endTag(null, "method");

        // method_universal_transverse_mercator
        serializer.startTag(null, "method");
        serializer.attribute(null, "id", "method_universal_transverse_mercator");
        serializer.startTag(null, "name");
        serializer.text("Universal Transverse Mercator coordinate system");
        serializer.endTag(null, "name");
        serializer.startTag(null, "description");
        serializer.endTag(null, "description");
        serializer.startTag(null, "subject");
        serializer.text("Coordinate system");
        serializer.endTag(null, "subject");
        serializer.startTag(null, "citationString");
        serializer.endTag(null, "citationString");
        serializer.endTag(null, "method");

        Cursor plotCursor = db.rawQuery("Select * FROM plot WHERE project_id =  \"" + MainActivity.exportedProjectId + "\"", null);
        String _id;
        try {
            while (plotCursor.moveToNext()) {
                _id = plotCursor.getString(plotCursor.getColumnIndex("_id"));
                String spatial_reference = plotCursor.getString(plotCursor.getColumnIndex("spatial_reference"));
                if (spatial_reference != null && !spatial_reference.isEmpty()) {
                    if (!spatialReferenceLookup.containsKey(spatial_reference)) {
                        spatialReferenceLookup.put(spatial_reference, _id);
                        // method_geographic_latitude_longitude_in_decimal_degrees with spatial_reference
                        serializer.startTag(null, "method");
                        serializer.attribute(null, "id", "method_geographic_latitude_longitude_in_decimal_degrees_" + _id);
                        serializer.startTag(null, "name");
                        serializer.text("Geographic latitude and longitude");
                        serializer.endTag(null, "name");
                        serializer.startTag(null, "description");
                        serializer.text(spatial_reference);
                        serializer.endTag(null, "description");
                        serializer.startTag(null, "subject");
                        serializer.text("Coordinate system");
                        serializer.endTag(null, "subject");
                        serializer.startTag(null, "citationString");
                        serializer.endTag(null, "citationString");
                        serializer.endTag(null, "method");

                        // method_universal_transverse_mercator with spatial_reference
                        serializer.startTag(null, "method");
                        serializer.attribute(null, "id", "method_universal_transverse_mercator_" + _id);
                        serializer.startTag(null, "name");
                        serializer.text("Universal Transverse Mercator coordinate system");
                        serializer.endTag(null, "name");
                        serializer.startTag(null, "description");
                        serializer.text(spatial_reference);
                        serializer.endTag(null, "description");
                        serializer.startTag(null, "subject");
                        serializer.text("Coordinate system");
                        serializer.endTag(null, "subject");
                        serializer.startTag(null, "citationString");
                        serializer.endTag(null, "citationString");
                        serializer.endTag(null, "method");
                    }
                }

                String northing = plotCursor.getString(plotCursor.getColumnIndex("northing"));
                String easting = plotCursor.getString(plotCursor.getColumnIndex("easting"));
                if (northing != null && !northing.isEmpty() && easting != null && !easting.isEmpty()) {
                    String coordinate_system_zone = plotCursor.getString(plotCursor.getColumnIndex("coord_system_zone"));
                    CoordSpatialPair coordSpatialPair = new CoordSpatialPair(coordinate_system_zone, spatial_reference);
                    if (!coordinateUnitsLookup.containsKey(coordSpatialPair)) {
                        coordinateUnitsLookup.put(coordSpatialPair, _id);
                        // method_coordinate_units
                        serializer.startTag(null, "method");
                        serializer.attribute(null, "id", "method_coordinate_units_" + _id);
                        serializer.startTag(null, "name");
                        serializer.text("Coordinate system");
                        serializer.endTag(null, "name");
                        serializer.startTag(null, "description");
                        if (spatial_reference != null && coordinate_system_zone != null) {
                            serializer.text(spatial_reference + " " + coordinate_system_zone);
                        } else if (spatial_reference != null) {
                            serializer.text(spatial_reference);
                        } else if (coordinate_system_zone != null) {
                            serializer.text(coordinate_system_zone);
                        }
                        serializer.endTag(null, "description");
                        serializer.startTag(null, "subject");
                        serializer.text("Coordinate system");
                        serializer.endTag(null, "subject");
                        serializer.startTag(null, "citationString");
                        serializer.endTag(null, "citationString");
                        serializer.endTag(null, "method");
                    }
                }
            }
        } finally {
            plotCursor.close();
        }

        // method_aspect_class
        serializer.startTag(null, "method");
        serializer.attribute(null, "id", "method_aspect_class");
        serializer.startTag(null, "name");
        serializer.text("Aspect classes");
        serializer.endTag(null, "name");
        serializer.startTag(null, "description");
        serializer.text("Aspect class such as N, S, E, etc.");
        serializer.endTag(null, "description");
        serializer.startTag(null, "subject");
        serializer.text("Aspect");
        serializer.endTag(null, "subject");
        serializer.startTag(null, "citationString");
        serializer.endTag(null, "citationString");
        serializer.endTag(null, "method");

        Cursor individualsCursor = db.rawQuery("Select * FROM individuals WHERE project_id =  \"" + MainActivity.exportedProjectId + "\"", null);
        try {
            while (individualsCursor.moveToNext()) {
                String dbh_above_ground = individualsCursor.getString(individualsCursor.getColumnIndex("dbh_above_ground"));
                if (dbh_above_ground == null || dbh_above_ground.isEmpty()) {
                    dbh_above_ground = "unspecified";
                }
                if (!dbhAboveGroundLookup.contains(dbh_above_ground)) {
                    dbhAboveGroundLookup.add(dbh_above_ground);
                    // method_diameter_at_breast_height_in_cm
                    serializer.startTag(null, "method");
                    serializer.attribute(null, "id", "method_diameter_at_breast_height_in_cm_" + dbh_above_ground);
                    serializer.startTag(null, "name");
                    serializer.text("Diameter at breast height (DBH) in cm");
                    serializer.endTag(null, "name");
                    serializer.startTag(null, "description");
                    serializer.text("Diameter measured at " + dbh_above_ground + " m above ground");
                    serializer.endTag(null, "description");
                    serializer.startTag(null, "subject");
                    serializer.text("Diameter");
                    serializer.endTag(null, "subject");
                    serializer.startTag(null, "citationString");
                    serializer.endTag(null, "citationString");
                    serializer.endTag(null, "method");
                }
                String girth_above_ground = individualsCursor.getString(individualsCursor.getColumnIndex("girth_above_ground"));
                if (girth_above_ground == null || girth_above_ground.isEmpty()) {
                    girth_above_ground = "unspecified";
                }
                if (!girthAboveGroundLookup.contains(girth_above_ground)) {
                    girthAboveGroundLookup.add(girth_above_ground);
                    // method_girth_at_breast_height_in_cm
                    serializer.startTag(null, "method");
                    serializer.attribute(null, "id", "method_girth_at_breast_height_in_cm_" + girth_above_ground);
                    serializer.startTag(null, "name");
                    serializer.text("Girth at breast height in cm");
                    serializer.endTag(null, "name");
                    serializer.startTag(null, "description");
                    serializer.text("Girth measured at " + girth_above_ground + " m above ground");
                    serializer.endTag(null, "description");
                    serializer.startTag(null, "subject");
                    serializer.text("Girth");
                    serializer.endTag(null, "subject");
                    serializer.startTag(null, "citationString");
                    serializer.endTag(null, "citationString");
                    serializer.endTag(null, "method");
                }

                String protocol = individualsCursor.getString(individualsCursor.getColumnIndex("protocol"));
                String individualId = individualsCursor.getString(individualsCursor.getColumnIndex("_id"));
                String polar_coordinate_direction = individualsCursor.getString(individualsCursor.getColumnIndex("direction_origin"));
                if (polar_coordinate_direction != null && !polar_coordinate_direction.isEmpty()) {
                    if (!polarCoordinateDirectionLookup.containsKey(protocol)) {
                        polarCoordinateDirectionLookup.put(protocol, individualId);
                        serializer.startTag(null, "method");
                        serializer.attribute(null, "id", "method_polar_coordinate_direction_" + individualId);
                        serializer.startTag(null, "name");
                        serializer.text("Direction of an individual relative to a location as part of a polar coordinate");
                        serializer.endTag(null, "name");
                        serializer.startTag(null, "description");
                        if (protocol != null) {
                            serializer.text(protocol);
                        }
                        serializer.endTag(null, "description");
                        serializer.startTag(null, "subject");
                        serializer.text("Location of an individual");
                        serializer.endTag(null, "subject");
                        serializer.startTag(null, "citationString");
                        serializer.endTag(null, "citationString");
                        serializer.endTag(null, "method");
                    }
                }
                String polar_coordinate_distance = individualsCursor.getString(individualsCursor.getColumnIndex("distance_origin"));
                if (polar_coordinate_distance != null && !polar_coordinate_distance.isEmpty()) {
                    if (!polarCoordinateDistanceLookup.containsKey(protocol)) {
                        polarCoordinateDistanceLookup.put(protocol, individualId);
                        serializer.startTag(null, "method");
                        serializer.attribute(null, "id", "method_polar_coordinate_distance_" + individualId);
                        serializer.startTag(null, "name");
                        serializer.text("Distance of an individual as part of a polar coordinate");
                        serializer.endTag(null, "name");
                        serializer.startTag(null, "description");
                        if (protocol != null) {
                            serializer.text(protocol);
                        }
                        serializer.endTag(null, "description");
                        serializer.startTag(null, "subject");
                        serializer.text("Location of an individual");
                        serializer.endTag(null, "subject");
                        serializer.startTag(null, "citationString");
                        serializer.endTag(null, "citationString");
                        serializer.endTag(null, "method");
                    }
                }
                String individual_location_quarter = individualsCursor.getString(individualsCursor.getColumnIndex("quarter"));
                if (individual_location_quarter != null && !individual_location_quarter.isEmpty()) {
                    QuarterProtocolPair quarterProtocolPair = new QuarterProtocolPair(individual_location_quarter, protocol);
                    individualLocationQuarterProtocolSet.add(quarterProtocolPair);
                    if (!individualLocationQuarterLookup.containsKey(protocol)) {
                        individualLocationQuarterLookup.put(protocol, individualId);
                        serializer.startTag(null, "method");
                        serializer.attribute(null, "id", "method_individual_location_quarter_" + individualId);
                        serializer.startTag(null, "name");
                        serializer.text("Location of an individual in a plot quadrant");
                        serializer.endTag(null, "name");
                        serializer.startTag(null, "description");
                        if (protocol != null) {
                            serializer.text(protocol);
                        }
                        serializer.endTag(null, "description");
                        serializer.startTag(null, "subject");
                        serializer.text("Location of an individual");
                        serializer.endTag(null, "subject");
                        serializer.startTag(null, "citationString");
                        serializer.endTag(null, "citationString");
                        serializer.endTag(null, "method");
                    }
                }

                Cursor custom_fields_cursor = db.rawQuery("select * from custom_fields where profile = \"" + profileId + "\"", null);
                if (custom_fields_cursor.moveToFirst()) {
                    try {
                        do {
                            String id = "method_" + custom_fields_cursor.getString(custom_fields_cursor.getColumnIndex("attribute")) + "_" + custom_fields_cursor.getString(custom_fields_cursor.getColumnIndex("scope"));
                            String name = custom_fields_cursor.getString(custom_fields_cursor.getColumnIndex("name"));
                            String description = custom_fields_cursor.getString(custom_fields_cursor.getColumnIndex("method"));
                            String subject = custom_fields_cursor.getString(custom_fields_cursor.getColumnIndex("scope"));
                            String attribute = custom_fields_cursor.getString(custom_fields_cursor.getColumnIndex("attribute"));

                            if (attribute == null) {
                                attribute = "";
                            }

                            if (name == null) {
                                name = "";
                            } else if (description == null) {
                                description = "";
                            } else if (subject == null) {
                                subject = "";
                            }

                            Method method = new Method(id, name, description, subject, "");

                            if (!checkInMethods(method)) {
                                serializer.startTag(null, "method");
                                serializer.attribute(null, "id", id);
                                serializer.startTag(null, "name");
                                serializer.text(attribute);
                                serializer.endTag(null, "name");
                                serializer.startTag(null, "description");
                                serializer.text(description);
                                serializer.endTag(null, "description");
                                serializer.startTag(null, "subject");
                                serializer.text(subject);
                                serializer.endTag(null, "subject");
                                serializer.startTag(null, "citationString");
                                //serializer.text(/* stays empty */ "");
                                serializer.endTag(null, "citationString");
                                serializer.endTag(null, "method");

                                methods.add(method);
                            }
                        } while (custom_fields_cursor.moveToNext());
                    } finally {
                        custom_fields_cursor.close();
                    }
                }
            }
        } finally {
            individualsCursor.close();
        }

    }

    private boolean checkInMethods(Method method) {
        for (Method m : methods) {
            if (m.getId().equals(method.getId())) {
                return true;
            }
        }
        return false;
    }

    //************************************************************//
    //********************* attributes  **************************//
    //************************************************************//

    private void writeAttributes(XmlSerializer serializer) throws IOException {

        serializer.startTag(null, "attributes");

        // location_accuracy_in_meter
        serializer.startTag(null, "attribute");
        serializer.attribute(null, "id", "location_accuracy_in_meter");
        serializer.startTag(null, "quantitative");
        serializer.startTag(null, "methodID");
        serializer.text("method_location_accuracy_in_meter");
        serializer.endTag(null, "methodID");
        serializer.startTag(null, "unit");
        serializer.text("m");
        serializer.endTag(null, "unit");
        serializer.endTag(null, "quantitative");
        serializer.endTag(null, "attribute");

        // plot_radius_in_meter
        serializer.startTag(null, "attribute");
        serializer.attribute(null, "id", "plot_radius_in_meter");
        serializer.startTag(null, "quantitative");
        serializer.startTag(null, "methodID");
        serializer.text("method_plot_radius_in_meter");
        serializer.endTag(null, "methodID");
        serializer.startTag(null, "unit");
        serializer.text("m");
        serializer.endTag(null, "unit");
        serializer.endTag(null, "quantitative");
        serializer.endTag(null, "attribute");

        // plot_width_in_meter
        serializer.startTag(null, "attribute");
        serializer.attribute(null, "id", "plot_width_in_meter");
        serializer.startTag(null, "quantitative");
        serializer.startTag(null, "methodID");
        serializer.text("method_plot_width_in_meter");
        serializer.endTag(null, "methodID");
        serializer.startTag(null, "unit");
        serializer.text("m");
        serializer.endTag(null, "unit");
        serializer.endTag(null, "quantitative");
        serializer.endTag(null, "attribute");

        // plot_length_in_meter
        serializer.startTag(null, "attribute");
        serializer.attribute(null, "id", "plot_length_in_meter");
        serializer.startTag(null, "quantitative");
        serializer.startTag(null, "methodID");
        serializer.text("method_plot_length_in_meter");
        serializer.endTag(null, "methodID");
        serializer.startTag(null, "unit");
        serializer.text("m");
        serializer.endTag(null, "unit");
        serializer.endTag(null, "quantitative");
        serializer.endTag(null, "attribute");

        // maximum_height_of_layer_in_meter
        serializer.startTag(null, "attribute");
        serializer.attribute(null, "id", "maximum_height_of_layer_in_meter");
        serializer.startTag(null, "quantitative");
        serializer.startTag(null, "methodID");
        serializer.text("method_maximum_height_of_layer_in_meter");
        serializer.endTag(null, "methodID");
        serializer.startTag(null, "unit");
        serializer.text("m");
        serializer.endTag(null, "unit");
        serializer.endTag(null, "quantitative");
        serializer.endTag(null, "attribute");

        // average_stratum_height_in_meter
        serializer.startTag(null, "attribute");
        serializer.attribute(null, "id", "average_stratum_height_in_meter");
        serializer.startTag(null, "quantitative");
        serializer.startTag(null, "methodID");
        serializer.text("method_average_stratum_height_in_meter");
        serializer.endTag(null, "methodID");
        serializer.startTag(null, "unit");
        serializer.text("m");
        serializer.endTag(null, "unit");
        serializer.endTag(null, "quantitative");
        serializer.endTag(null, "attribute");

        // elevation_above_sea_level_in_meter
        serializer.startTag(null, "attribute");
        serializer.attribute(null, "id", "elevation_above_sea_level_in_meter");
        serializer.startTag(null, "quantitative");
        serializer.startTag(null, "methodID");
        serializer.text("method_elevation_above_sea_level_in_meter");
        serializer.endTag(null, "methodID");
        serializer.startTag(null, "unit");
        serializer.text("m");
        serializer.endTag(null, "unit");
        serializer.endTag(null, "quantitative");
        serializer.endTag(null, "attribute");

        // location_accuracy_in_dop
        serializer.startTag(null, "attribute");
        serializer.attribute(null, "id", "location_accuracy_in_dop");
        serializer.startTag(null, "quantitative");
        serializer.startTag(null, "methodID");
        serializer.text("method_location_accuracy_in_dop");
        serializer.endTag(null, "methodID");
        serializer.startTag(null, "unit");
        serializer.text("Geometric Dilution of Precision (DOP)");
        serializer.endTag(null, "unit");
        serializer.endTag(null, "quantitative");
        serializer.endTag(null, "attribute");

        // plot_surface_in_square_meter
        serializer.startTag(null, "attribute");
        serializer.attribute(null, "id", "plot_surface_in_square_meter");
        serializer.startTag(null, "quantitative");
        serializer.startTag(null, "methodID");
        serializer.text("method_plot_surface_in_square_meter");
        serializer.endTag(null, "methodID");
        serializer.startTag(null, "unit");
        serializer.text("m²");
        serializer.endTag(null, "unit");
        serializer.endTag(null, "quantitative");
        serializer.endTag(null, "attribute");

        // plot_orientation_in_degrees
        serializer.startTag(null, "attribute");
        serializer.attribute(null, "id", "plot_orientation_in_degrees");
        serializer.startTag(null, "quantitative");
        serializer.startTag(null, "methodID");
        serializer.text("method_plot_orientation_in_degrees");
        serializer.endTag(null, "methodID");
        serializer.startTag(null, "unit");
        serializer.text("°");
        serializer.endTag(null, "unit");
        serializer.endTag(null, "quantitative");
        serializer.endTag(null, "attribute");

        // aspect_in_degrees
        serializer.startTag(null, "attribute");
        serializer.attribute(null, "id", "aspect_in_degrees");
        serializer.startTag(null, "quantitative");
        serializer.startTag(null, "methodID");
        serializer.text("method_aspect_in_degrees");
        serializer.endTag(null, "methodID");
        serializer.startTag(null, "unit");
        serializer.text("°");
        serializer.endTag(null, "unit");
        serializer.endTag(null, "quantitative");
        serializer.endTag(null, "attribute");

        // slope_in_degrees
        serializer.startTag(null, "attribute");
        serializer.attribute(null, "id", "slope_in_degrees");
        serializer.startTag(null, "quantitative");
        serializer.startTag(null, "methodID");
        serializer.text("method_slope_in_degrees");
        serializer.endTag(null, "methodID");
        serializer.startTag(null, "unit");
        serializer.text("°");
        serializer.endTag(null, "unit");
        serializer.endTag(null, "quantitative");
        serializer.endTag(null, "attribute");

        // percent_cover_of_species
        serializer.startTag(null, "attribute");
        serializer.attribute(null, "id", "percent_cover_of_species");
        serializer.startTag(null, "quantitative");
        serializer.startTag(null, "methodID");
        serializer.text("method_percent_cover_of_species");
        serializer.endTag(null, "methodID");
        serializer.startTag(null, "unit");
        serializer.text("%");
        serializer.endTag(null, "unit");
        serializer.endTag(null, "quantitative");
        serializer.endTag(null, "attribute");

        // percent_cover_of_surface_material
        serializer.startTag(null, "attribute");
        serializer.attribute(null, "id", "percent_cover_of_surface_material");
        serializer.startTag(null, "quantitative");
        serializer.startTag(null, "methodID");
        serializer.text("method_percent_cover_of_surface_material");
        serializer.endTag(null, "methodID");
        serializer.startTag(null, "unit");
        serializer.text("%");
        serializer.endTag(null, "unit");
        serializer.endTag(null, "quantitative");
        serializer.endTag(null, "attribute");

        // percent_cover_of_layer_stratum
        serializer.startTag(null, "attribute");
        serializer.attribute(null, "id", "percent_cover_of_layer_stratum");
        serializer.startTag(null, "quantitative");
        serializer.startTag(null, "methodID");
        serializer.text("method_percent_cover_of_layer_stratum");
        serializer.endTag(null, "methodID");
        serializer.startTag(null, "unit");
        serializer.text("%");
        serializer.endTag(null, "unit");
        serializer.endTag(null, "quantitative");
        serializer.endTag(null, "attribute");

        if (coverScale != null && !coverScale.equals("'00'")) {
            try{
            Cursor cover_scale_codes_cursor = db.rawQuery("select entry from cover_code_lu where code_id = \"" + (coverScale.substring(1, coverScale.length() - 1)) + "\"", null);
                String code_value;
                while (cover_scale_codes_cursor.moveToNext()) {
                    code_value = cover_scale_codes_cursor.getString(cover_scale_codes_cursor.getColumnIndex("entry"));
                    if (code_value != null && !code_value.isEmpty()) {
                        serializer.startTag(null, "attribute");
                        // remove whitespace from code_value for safe use in id
                        // note: should two cover_codes ever be only different in whitespace,
                        // this won't work and I hereby declare that cover_code stupid :)
                        serializer.attribute(null, "id", "attribute_coverscale_" + code_value.replaceAll("\\s+", ""));
                        serializer.startTag(null, "ordinal");
                        serializer.startTag(null, "methodID");
                        serializer.text("method_coverscale");
                        serializer.endTag(null, "methodID");
                        serializer.startTag(null, "code");
                        serializer.text(code_value);
                        serializer.endTag(null, "code");
                        serializer.endTag(null, "ordinal");
                        serializer.endTag(null, "attribute");
                    }
                }
                serializer.startTag(null, "attribute");
                serializer.attribute(null, "id", "attribute_coverscale_00");
                serializer.startTag(null, "ordinal");
                serializer.startTag(null, "methodID");
                serializer.text("method_coverscale");
                serializer.endTag(null, "methodID");
                serializer.startTag(null, "code");
                serializer.text("NA");
                serializer.endTag(null, "code");
                serializer.endTag(null, "ordinal");
                serializer.endTag(null, "attribute");

            } catch(Exception e) {
                e.printStackTrace();
            }
        } else if (coverScale == null) {
            /* TODO: is this fitting as a method for when no coverscale is selected? */
            serializer.startTag(null, "attribute");
            serializer.attribute(null, "id", "attribute_coverscale_null");
            serializer.startTag(null, "ordinal");
            serializer.startTag(null, "methodID");
            serializer.text("method_coverscale");
            serializer.endTag(null, "methodID");
            serializer.startTag(null, "code");
            serializer.text("NA");
            serializer.endTag(null, "code");
            serializer.endTag(null, "ordinal");
            serializer.endTag(null, "attribute");
        }

        // geographic_latitude_longitude_in_decimal_degrees
        serializer.startTag(null, "attribute");
        serializer.attribute(null, "id", "geographic_latitude_longitude_in_decimal_degrees");
        serializer.startTag(null, "quantitative");
        serializer.startTag(null, "methodID");
        serializer.text("method_geographic_latitude_longitude_in_decimal_degrees");
        serializer.endTag(null, "methodID");
        serializer.startTag(null, "unit");
        serializer.text("Decimal degrees");
        serializer.endTag(null, "unit");
        serializer.endTag(null, "quantitative");
        serializer.endTag(null, "attribute");

        // universal_transverse_mercator
        serializer.startTag(null, "attribute");
        serializer.attribute(null, "id", "universal_transverse_mercator");
        serializer.startTag(null, "quantitative");
        serializer.startTag(null, "methodID");
        serializer.text("method_universal_transverse_mercator");
        serializer.endTag(null, "methodID");
        serializer.startTag(null, "unit");
        serializer.text("m");
        serializer.endTag(null, "unit");
        serializer.endTag(null, "quantitative");
        serializer.endTag(null, "attribute");

        for (String value : spatialReferenceLookup.values()) {
            // geographic_latitude_longitude_in_decimal_degrees with spatial_reference
            serializer.startTag(null, "attribute");
            serializer.attribute(null, "id", "geographic_latitude_longitude_in_decimal_degrees_" + value);
            serializer.startTag(null, "quantitative");
            serializer.startTag(null, "methodID");
            serializer.text("method_geographic_latitude_longitude_in_decimal_degrees_" + value);
            serializer.endTag(null, "methodID");
            serializer.startTag(null, "unit");
            serializer.text("Decimal degrees");
            serializer.endTag(null, "unit");
            serializer.endTag(null, "quantitative");
            serializer.endTag(null, "attribute");

            // universal_transverse_mercator with spatial_refernece
            serializer.startTag(null, "attribute");
            serializer.attribute(null, "id", "universal_transverse_mercator_" + value);
            serializer.startTag(null, "quantitative");
            serializer.startTag(null, "methodID");
            serializer.text("method_universal_transverse_mercator_" + value);
            serializer.endTag(null, "methodID");
            serializer.startTag(null, "unit");
            serializer.text("m");
            serializer.endTag(null, "unit");
            serializer.endTag(null, "quantitative");
            serializer.endTag(null, "attribute");
        }

        for (Map.Entry<CoordSpatialPair, String> entry : coordinateUnitsLookup.entrySet()) {
            // coordinate_units
            serializer.startTag(null, "attribute");
            serializer.attribute(null, "id", "coordinate_units_" + entry.getValue());
            serializer.startTag(null, "quantitative");
            serializer.startTag(null, "methodID");
            serializer.text("method_coordinate_units_" + entry.getValue());
            serializer.endTag(null, "methodID");
            serializer.startTag(null, "unit");
            serializer.text(entry.getKey().getCoordinateUnits());
            serializer.endTag(null, "unit");
            serializer.endTag(null, "quantitative");
            serializer.endTag(null, "attribute");
        }

        String[] aspectClasses = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        for (String aspectClass : aspectClasses) {
            // aspect class attribute for each class
            serializer.startTag(null, "attribute");
            serializer.attribute(null, "id", "aspect_class_" + aspectClass);
            serializer.startTag(null, "qualitative");
            serializer.startTag(null, "methodID");
            serializer.text("method_aspect_class");
            serializer.endTag(null, "methodID");
            serializer.startTag(null, "code");
            serializer.text(aspectClass);
            serializer.endTag(null, "code");
            serializer.endTag(null, "qualitative");
            serializer.endTag(null, "attribute");
        }

        for (String dbhAboveGround : dbhAboveGroundLookup) {
            // diameter_at_breast_height_in_cm
            serializer.startTag(null, "attribute");
            serializer.attribute(null, "id", "diameter_at_breast_height_in_cm_" + dbhAboveGround);
            serializer.startTag(null, "quantitative");
            serializer.startTag(null, "methodID");
            serializer.text("method_diameter_at_breast_height_in_cm_" + dbhAboveGround);
            serializer.endTag(null, "methodID");
            serializer.startTag(null, "unit");
            serializer.text("cm");
            serializer.endTag(null, "unit");
            serializer.endTag(null, "quantitative");
            serializer.endTag(null, "attribute");
        }

        for (String girthAboveGround : girthAboveGroundLookup) {
            // girth_at_breast_height_in_cm
            serializer.startTag(null, "attribute");
            serializer.attribute(null, "id", "girth_at_breast_height_in_cm_" + girthAboveGround);
            serializer.startTag(null, "quantitative");
            serializer.startTag(null, "methodID");
            serializer.text("method_girth_at_breast_height_in_cm_" + girthAboveGround);
            serializer.endTag(null, "methodID");
            serializer.startTag(null, "unit");
            serializer.text("cm");
            serializer.endTag(null, "unit");
            serializer.endTag(null, "quantitative");
            serializer.endTag(null, "attribute");
        }

        for (String value : polarCoordinateDirectionLookup.values()) {
            // polar coordinate direction
            serializer.startTag(null, "attribute");
            serializer.attribute(null, "id", "polar_coordinate_direction_" + value);
            serializer.startTag(null, "quantitative");
            serializer.startTag(null, "methodID");
            serializer.text("method_polar_coordinate_direction_" + value);
            serializer.endTag(null, "methodID");
            serializer.startTag(null, "unit");
            serializer.text("");
            serializer.endTag(null, "unit");
            serializer.endTag(null, "quantitative");
            serializer.endTag(null, "attribute");
        }

        for (String value : polarCoordinateDistanceLookup.values()) {
            // polar coordinate distance
            serializer.startTag(null, "attribute");
            serializer.attribute(null, "id", "polar_coordinate_distance_" + value);
            serializer.startTag(null, "quantitative");
            serializer.startTag(null, "methodID");
            serializer.text("method_polar_coordinate_distance_" + value);
            serializer.endTag(null, "methodID");
            serializer.startTag(null, "unit");
            serializer.text("");
            serializer.endTag(null, "unit");
            serializer.endTag(null, "quantitative");
            serializer.endTag(null, "attribute");
        }

        for (QuarterProtocolPair quarterProtocolPair : individualLocationQuarterProtocolSet) {
            // polar coordinate location in plot quarters
            String methodId = individualLocationQuarterLookup.get(quarterProtocolPair.getProtocol());
            serializer.startTag(null, "attribute");
            serializer.attribute(null, "id", "individual_location_quarter_" + methodId + "_" + quarterProtocolPair.getQuarter());
            serializer.startTag(null, "qualitative");
            serializer.startTag(null, "methodID");
            serializer.text("method_individual_location_quarter_" + methodId);
            serializer.endTag(null, "methodID");
            serializer.startTag(null, "code");
            serializer.text(quarterProtocolPair.getQuarter());
            serializer.endTag(null, "code");
            serializer.endTag(null, "qualitative");
            serializer.endTag(null, "attribute");
        }

        Cursor custom_fields_cursor_nominal = db.rawQuery("select * from custom_fields where level = \"" + nominal + "\" and profile = \"" + profileId + "\"", null);
        if (custom_fields_cursor_nominal.moveToFirst()) {
            try {
                do {
                    serializer.startTag(null, "attribute");
                    serializer.attribute(null, "id", custom_fields_cursor_nominal.getString(custom_fields_cursor_nominal.getColumnIndexOrThrow("attribute")) + "_" + custom_fields_cursor_nominal.getString(custom_fields_cursor_nominal.getColumnIndex("scope")));
                    serializer.startTag(null, "qualitative");
                    serializer.startTag(null, "methodID");
                    serializer.text("method_" + custom_fields_cursor_nominal.getString(custom_fields_cursor_nominal.getColumnIndexOrThrow("attribute")) + "_" + custom_fields_cursor_nominal.getString(custom_fields_cursor_nominal.getColumnIndex("scope")));
                    serializer.endTag(null, "methodID");
                    serializer.startTag(null, "code");
                    //serializer.text(dbh.getCoverScaleID(Integer.parseInt(projectId)));
                    serializer.text("");
                    serializer.endTag(null, "code");
                    serializer.endTag(null, "qualitative");
                    serializer.endTag(null, "attribute");
                } while (custom_fields_cursor_nominal.moveToNext());
            } finally {
                custom_fields_cursor_nominal.close();
            }
        }

        Cursor custom_fields_cursor_ordinal = db.rawQuery("select * from custom_fields where level = \"" + ordinal + "\" and profile = \"" + profileId + "\"", null);
        if (custom_fields_cursor_ordinal.moveToFirst()) {
            try {
                do {
                    serializer.startTag(null, "attribute");
                    serializer.attribute(null, "id", custom_fields_cursor_ordinal.getString(custom_fields_cursor_ordinal.getColumnIndexOrThrow("attribute")) + "_" + custom_fields_cursor_ordinal.getString(custom_fields_cursor_ordinal.getColumnIndex("scope")));
                    serializer.startTag(null, "ordinal");
                    serializer.startTag(null, "methodID");
                    serializer.text("method_" + custom_fields_cursor_ordinal.getString(custom_fields_cursor_ordinal.getColumnIndexOrThrow("attribute")) + "_" + custom_fields_cursor_ordinal.getString(custom_fields_cursor_ordinal.getColumnIndex("scope")));
                    serializer.endTag(null, "methodID");
                    serializer.startTag(null, "code");
                    //serializer.text(dbh.getCoverScaleID(Integer.parseInt(projectId)));
                    serializer.text("");
                    serializer.endTag(null, "code");
                    serializer.endTag(null, "ordinal");
                    serializer.endTag(null, "attribute");
                } while (custom_fields_cursor_ordinal.moveToNext());
            } finally {
                custom_fields_cursor_ordinal.close();
            }
        }

        Cursor custom_fields_cursor_interval_ratio = db.rawQuery("select * from custom_fields where level = \"" + interval_ratio + "\" and profile = \"" + profileId + "\"", null);
        if (custom_fields_cursor_interval_ratio.moveToFirst()) {
            try {
                do {
                    serializer.startTag(null, "attribute");
                    serializer.attribute(null, "id", custom_fields_cursor_interval_ratio.getString(custom_fields_cursor_interval_ratio.getColumnIndex("attribute")) + "_" + custom_fields_cursor_interval_ratio.getString(custom_fields_cursor_interval_ratio.getColumnIndex("scope")));
                    serializer.startTag(null, "quantitative");
                    serializer.startTag(null, "methodID");
                    serializer.text("method_" + custom_fields_cursor_interval_ratio.getString(custom_fields_cursor_interval_ratio.getColumnIndex("attribute")) + "_" + custom_fields_cursor_interval_ratio.getString(custom_fields_cursor_interval_ratio.getColumnIndex("scope")));
                    serializer.endTag(null, "methodID");
                    serializer.startTag(null, "unit");
                    String value = custom_fields_cursor_interval_ratio.getString(custom_fields_cursor_interval_ratio.getColumnIndexOrThrow("units"));
                    if (value != null && !value.isEmpty()) {
                    serializer.text(value);
                    }
                    serializer.endTag(null, "unit");
                    serializer.endTag(null, "quantitative");
                    serializer.endTag(null, "attribute");
                } while (custom_fields_cursor_interval_ratio.moveToNext());
            } finally {
                custom_fields_cursor_interval_ratio.close();
            }
        }

        serializer.endTag(null, "attributes");
    }

    //************************************************************//
    //********************** strata  *****************************//
    //************************************************************//

    private void writeStrata(XmlSerializer serializer) throws IOException {

        if (MainActivity.layerArrayList == null) {
            MainActivity.layerArrayList = LayerArrayListHelper.getList();
        }
        serializer.startTag(null, "strata");
        int i = 0;

        for (Layer layer : MainActivity.layerArrayList) {
            serializer.startTag(null, "stratum");
            serializer.attribute(null, "id", "stratum_" + layer.getID());
            serializer.startTag(null, "stratumName");
            if (layer.getName() != null) {
                serializer.text(layer.getName());
            }
            serializer.endTag(null, "stratumName");
            serializer.startTag(null, "methodID");
            Cursor layerSystemsCursor = db.rawQuery("SELECT * FROM layer_system WHERE _id = " + layersystemID, null);
            layerSystemsCursor.moveToFirst();
            String layer_name = layerSystemsCursor.getString(layerSystemsCursor.getColumnIndex("name"));
            if (layer_name.equals("Default")) {
                serializer.text("defaultLayerSystem");
            } else {
                serializer.text("customLayerSystem");
            }
            layerSystemsCursor.close();
            serializer.endTag(null, "methodID");
            serializer.startTag(null, "definition");
            if (layer.getCriteria() != null && !layer.getCriteria().isEmpty()) {
                serializer.text(layer.getCriteria());
            } else if (layer.getCriteriaDefault() != null) {
                serializer.text(layer.getCriteriaDefault());
            }
            serializer.endTag(null, "definition");
            serializer.startTag(null, "order");
            i++;
            serializer.text(String.valueOf(i));
            serializer.endTag(null, "order");

            if (layer.getMaxHeight() != null) {
                serializer.startTag(null, "upperLimit");
                serializer.text(layer.getMaxHeight());
                serializer.endTag(null, "upperLimit");
            }
            if (layer.getMinHeight() != null) {
                serializer.startTag(null, "lowerLimit");
                serializer.text(layer.getMinHeight());
                serializer.endTag(null, "lowerLimit");
            }
            serializer.endTag(null, "stratum");
        }
        serializer.endTag(null, "strata");
    }

    //************************************************************//
    //******************** surfaceTypes  *************************//
    //************************************************************//

    // String[i][0] text of surfaceName tag
    // String[i][1] database column for value tag
    private static final String[][] surfaceTypes = new String[][]{
            {"Live Plants (%)", "foliage"},
            {"Standing deadwood (%)", "standing_dead"},
            {"Lying deadwood (%)", "dead_stems"},
            {"Moribund (%)", "moribund"},
            {"Litter (%)", "litter"},
            {"Bare rock (%)", "bare_rock"},
            {"Bare soil(%)", "bare_soil"},
            {"Bare ground (%)", "bare_ground"},
            {"Open water (%)", "open_water"},
            {"Live vascular (%)", "live_vascular"},
            {"Live non-vascular (%)", "live_non_vascular"}
    };

    private void writeSurfaceTypes(XmlSerializer serializer) throws IOException {

        serializer.startTag(null, "surfaceTypes");

        /* write normal surfaceTypes */
        for (int i = 0; i < surfaceTypes.length; i++) {
            serializer.startTag(null, "surfaceType");
            serializer.attribute(null, "id", "surfaceType_" + surfaceTypes[i][1]);
            serializer.startTag(null, "surfaceName");
            serializer.text(surfaceTypes[i][0]);
            serializer.endTag(null, "surfaceName");
            serializer.endTag(null, "surfaceType");
        }

        /* write custom surfaceTypes */
        Cursor custom_fields_cursor = db.rawQuery("select * from custom_fields where type = \"" + surface_cover + "\" and profile = \"" + profileId + "\"", null);
        if (custom_fields_cursor.moveToFirst()) {
            try {
                do {
                    serializer.startTag(null, "surfaceType");
                    serializer.attribute(null, "id", "surfaceType_" + custom_fields_cursor.getString(custom_fields_cursor.getColumnIndex("name")));
                    serializer.startTag(null, "surfaceName");
                    serializer.text(custom_fields_cursor.getString(custom_fields_cursor.getColumnIndex("attribute")));
                    serializer.endTag(null, "surfaceName");
                    serializer.endTag(null, "surfaceType");
                } while (custom_fields_cursor.moveToNext());
            } finally {
                custom_fields_cursor.close();
            }
        }

        serializer.endTag(null, "surfaceTypes");
    }

    //************************************************************//
    //******************** organismNames  ************************//
    //************************************************************//

    private void writeOrganismNames(XmlSerializer serializer) throws IOException {

        //cursors
        Cursor plotCursor = db.rawQuery("Select * FROM plot WHERE project_id =  \"" + MainActivity.exportedProjectId + "\"", null);
        String _id;
        boolean started = false;
        try {
            while (plotCursor.moveToNext()) {
                _id = plotCursor.getString(plotCursor.getColumnIndex("_id"));
                Cursor speciesCursor = db.rawQuery("Select * FROM species WHERE plot_id =  \"" + _id + "\"", null);
                try {
                    while (speciesCursor.moveToNext()) {
                        String speciesId = speciesCursor.getString(speciesCursor.getColumnIndex("_id"));
                        if (speciesId != null && !speciesId.isEmpty()) {
                            if (!started) {
                                serializer.startTag(null, "organismNames");
                                started = true;
                            }
                            serializer.startTag(null, "organismName");
                            serializer.attribute(null, "id", "organismName_" + speciesId);
                            serializer.attribute(null, "taxonName", "true");
                            String organismName = "";
                            String value;
                            int intval;
                            intval = speciesCursor.getInt(speciesCursor.getColumnIndex("genus_cf"));
                            if (intval == 1) {
                                if (organismName.isEmpty()) {
                                    organismName += "cf.";
                                } else {
                                    organismName += " cf.";
                                }
                            }
                            value = speciesCursor.getString(speciesCursor.getColumnIndex("genus"));
                            if (value != null && !value.isEmpty()) {
                                if (organismName.isEmpty()) {
                                    organismName += value;
                                } else {
                                    organismName += " " + value;
                                }
                            }
                            intval = speciesCursor.getInt(speciesCursor.getColumnIndex("spec_cf"));
                            if (intval == 1) {
                                if (organismName.isEmpty()) {
                                    organismName += "cf.";
                                } else {
                                    organismName += " cf.";
                                }
                            }
                            value = speciesCursor.getString(speciesCursor.getColumnIndex("spec"));
                            if (value != null && !value.isEmpty()) {
                                if (organismName.isEmpty()) {
                                    organismName += value;
                                } else {
                                    organismName += " " + value;
                                }
                            }

                            serializer.text(organismName);

                            serializer.endTag(null, "organismName");
                        }
                    }
                } finally {
                    speciesCursor.close();
                }



                //and now for individuals



                Cursor individualsCursor = db.rawQuery("Select * FROM individuals WHERE plot_id =  \"" + _id + "\"", null);
                try {
                    while (individualsCursor.moveToNext()) {
                        String individualId = individualsCursor.getString(individualsCursor.getColumnIndex("_id"));
                        if (individualId != null && !individualId.isEmpty()) {
                            if (!started) {
                                serializer.startTag(null, "organismNames");
                                started = true;
                            }
                            serializer.startTag(null, "organismName");
                            serializer.attribute(null, "id", "organismName_" + individualId + "_i" );
                            serializer.attribute(null, "taxonName", "true");
                            String organismName = "";
                            String value;
                            int intval;
                            intval = individualsCursor.getInt(individualsCursor.getColumnIndex("genus_cf"));
                            if (intval == 1) {
                                if (organismName.isEmpty()) {
                                    organismName += "cf.";
                                } else {
                                    organismName += " cf.";
                                }
                            }
                            value = individualsCursor.getString(individualsCursor.getColumnIndex("genus"));
                            if (value != null && !value.isEmpty()) {
                                if (organismName.isEmpty()) {
                                    organismName += value;
                                } else {
                                    organismName += " " + value;
                                }
                            }
                            intval = individualsCursor.getInt(individualsCursor.getColumnIndex("spec_cf"));
                            if (intval == 1) {
                                if (organismName.isEmpty()) {
                                    organismName += "cf.";
                                } else {
                                    organismName += " cf.";
                                }
                            }
                            value = individualsCursor.getString(individualsCursor.getColumnIndex("spec"));
                            if (value != null && !value.isEmpty()) {
                                if (organismName.isEmpty()) {
                                    organismName += value;
                                } else {
                                    organismName += " " + value;
                                }
                            }

                            serializer.text(organismName);

                            serializer.endTag(null, "organismName");
                        }
                    }
                } finally {
                    individualsCursor.close();
                }






            }
        } finally {
            plotCursor.close();
        }

        if (started) {
            serializer.endTag(null, "organismNames");
        }
        
        
        
        
        
    }

    //************************************************************//
    //******************** taxonConcepts  ************************//
    //************************************************************//

    private void writeTaxonConcepts(XmlSerializer serializer) throws IOException {

        //cursors
        Cursor plotCursor = db.rawQuery("Select * FROM plot WHERE project_id =  \"" + MainActivity.exportedProjectId + "\"", null);
        String _id;
        boolean started = false;
        try {
            while (plotCursor.moveToNext()) {
                _id = plotCursor.getString(plotCursor.getColumnIndex("_id"));
                Cursor speciesCursor = db.rawQuery("Select * FROM species WHERE plot_id =  \"" + _id + "\"", null);
                try {
                    while (speciesCursor.moveToNext()) {
                        String speciesId = speciesCursor.getString(speciesCursor.getColumnIndex("_id"));
                        if (speciesId != null && !speciesId.isEmpty()) {
                            String taxonConcept = speciesCursor.getString(speciesCursor.getColumnIndex("taxon_concept"));
                            if (taxonConcept != null && !taxonConcept.isEmpty()) {
                                if (!started) {
                                    serializer.startTag(null, "taxonConcepts");
                                    started = true;
                                }
                                serializer.startTag(null, "taxonConcept");
                                serializer.attribute(null, "id", "taxonConcept_species_" + speciesId);
                                serializer.startTag(null, "organismNameID");
                                serializer.text("organismName_" + speciesId);
                                serializer.endTag(null, "organismNameID");
                                serializer.startTag(null, "accordingToCitationID");
                                serializer.text("taxonConcept_citation_species_" + speciesId);
                                serializer.endTag(null, "accordingToCitationID");
                                serializer.endTag(null, "taxonConcept");
                            }
                        }
                    }
                } finally {
                    speciesCursor.close();
                }
                Cursor individualsCursor = db.rawQuery("Select * FROM individuals WHERE plot_id =  \"" + _id + "\"", null);
                try {
                    while (individualsCursor.moveToNext()) {
                        String individualsId = individualsCursor.getString(speciesCursor.getColumnIndex("_id"));
                        if (individualsId != null && !individualsId.isEmpty()) {
                            String taxonConcept = individualsCursor.getString(individualsCursor.getColumnIndex("taxon_concept"));
                            if (taxonConcept != null && !taxonConcept.isEmpty()) {
                                if (!started) {
                                    serializer.startTag(null, "taxonConcepts");
                                    started = true;
                                }
                                serializer.startTag(null, "taxonConcept");
                                serializer.attribute(null, "id", "taxonConcept_individual_" + individualsId);
                                serializer.startTag(null, "organismNameID");
                                serializer.text("organismName_" + individualsId);
                                serializer.endTag(null, "organismNameID");
                                serializer.startTag(null, "accordingToCitationID");
                                serializer.text("taxonConcept_citation_individual_" + individualsId);
                                serializer.endTag(null, "accordingToCitationID");
                                serializer.endTag(null, "taxonConcept");
                            }
                        }
                    }
                } finally {
                    individualsCursor.close();
                }
            }
        } finally {
            plotCursor.close();
        }
        if (started) {
            serializer.endTag(null, "taxonConcepts");
        }
    }

    //************************************************************//
    //****************** organismIdentities  **********************//
    //************************************************************//

    private void writeOrganismIdentities(XmlSerializer serializer) throws IOException {

        //cursors
        Cursor plotCursor = db.rawQuery("Select * FROM plot WHERE project_id =  \"" + MainActivity.exportedProjectId + "\"", null);
        String _id;
        serializer.startTag(null, "organismIdentities");


        try {
            while (plotCursor.moveToNext()) {
                _id = plotCursor.getString(plotCursor.getColumnIndex("_id"));


                Cursor speciesCursor = db.rawQuery("Select * FROM species WHERE plot_id =  \"" + _id + "\"", null);
                try {
                    while (speciesCursor.moveToNext()) {
                        String speciesId = speciesCursor.getString(speciesCursor.getColumnIndex("_id"));
                        String taxonConcept = speciesCursor.getString(speciesCursor.getColumnIndex("taxon_concept"));
                        if (speciesId != null && !speciesId.isEmpty()) {
                            serializer.startTag(null, "organismIdentity");
                            serializer.attribute(null, "id", "organismIdentitiy_" + speciesId);
                            serializer.startTag(null, "originalOrganismNameID");
                            serializer.text("organismName_" + speciesId);
                            serializer.endTag(null, "originalOrganismNameID");

                            serializer.startTag(null, "originalIdentificationConcept");
                            serializer.startTag(null, "taxonConceptID");
                            if(taxonConcept != null && !taxonConcept.isEmpty()) {
                                serializer.text("taxonConcept_species_" + taxonConcept);
                            }
                            serializer.endTag(null, "taxonConceptID");
                            serializer.endTag(null, "originalIdentificationConcept");
                            serializer.endTag(null, "organismIdentity");
                        }
                    }
                } finally {
                    speciesCursor.close();
                }


                
                //And now for individuals
                Cursor individualCursor = db.rawQuery("Select * FROM individuals WHERE plot_id =  \"" + _id + "\"", null);
                try {
                    while (individualCursor.moveToNext()) {
                        String speciesId = individualCursor.getString(individualCursor.getColumnIndex("_id"));
                        String taxonConcept = individualCursor.getString(individualCursor.getColumnIndex("taxon_concept"));
                        if (speciesId != null && !speciesId.isEmpty()) {
                            serializer.startTag(null, "organismIdentity");
                            serializer.attribute(null, "id", "organismIdentitiy_" + speciesId  );
                            serializer.startTag(null, "originalOrganismNameID");
                            serializer.text("organismName_" + speciesId + "_i");
                            serializer.endTag(null, "originalOrganismNameID");

                            serializer.startTag(null, "originalIdentificationConcept");
                            serializer.startTag(null, "taxonConceptID");
                            if(taxonConcept != null && !taxonConcept.isEmpty()) {
                                serializer.text("taxonConcept_species_" + taxonConcept);
                            }
                            serializer.endTag(null, "taxonConceptID");
                            serializer.endTag(null, "originalIdentificationConcept");
                            serializer.endTag(null, "organismIdentity");
                        }
                    }
                } finally {
                    individualCursor.close();
                }
                

            }
        } finally {
            plotCursor.close();
        }





        serializer.endTag(null, "organismIdentities");

    }

    //************************************************************//
    //****************** communityConcepts  **********************//
    //************************************************************//


    // String[i][0] text of name tag
    // String[i][1] database column for value tag
    private static final String[][] communityConcepts = new String[][]{
            {"Subassociation", "subassociation"},
            {"Association", "association"},
            {"Alliance", "alliance"},
            {"Community", "community"},
            {"Formation", "formation"}
    };

    private void writeCommunityConcepts(XmlSerializer serializer) throws IOException {

        //cursors
        Cursor plotCursor = db.rawQuery("Select * FROM plot WHERE project_id =  \"" + MainActivity.exportedProjectId + "\"", null);
        String _id, value;
        boolean started = false;
        try {
            while (plotCursor.moveToNext()) {
                _id = plotCursor.getString(plotCursor.getColumnIndex("_id"));

                for (int i = 0; i < communityConcepts.length; i++) {
                    value = plotCursor.getString(plotCursor.getColumnIndex(communityConcepts[i][1]));
                    if (value != null && !value.isEmpty()) {
                        if (!started) {
                            serializer.startTag(null, "communityConcepts");
                            started = true;
                        }
                        serializer.startTag(null, "communityConcept");
                        serializer.attribute(null, "id", "communityConcept_" + _id + "_" + communityConcepts[i][1]);
                        serializer.startTag(null, "name");
                        serializer.text(value);
                        serializer.endTag(null, "name");
                        serializer.startTag(null, "rank");
                        serializer.text(communityConcepts[i][0]);
                        serializer.endTag(null, "rank");
                        serializer.endTag(null, "communityConcept");
                    }
                }
            }
        } finally {
            plotCursor.close();
        }
        if (started) {
            serializer.endTag(null, "communityConcepts");
        }
    }

    //************************************************************//
    //**************** communityDeterminations ********************//
    //************************************************************//

    private void writeCommunityDeterminations(XmlSerializer serializer) throws IOException {

        //cursors
        Cursor plotCursor = db.rawQuery("Select * FROM plot WHERE project_id =  \"" + MainActivity.exportedProjectId + "\"", null);
        String _id, value;
        boolean started = false;
        try {
            while (plotCursor.moveToNext()) {
                _id = plotCursor.getString(plotCursor.getColumnIndex("_id"));

                boolean startedRelation = false;
                for (int i = 0; i < communityConcepts.length; i++) {
                    value = plotCursor.getString(plotCursor.getColumnIndex(communityConcepts[i][1]));
                    if (value != null && !value.isEmpty()) {
                        // insgesamt nur anfangen, wenn es auch relations gibt, da erforderlich
                        if (!startedRelation) {
                            if (!started) {
                                serializer.startTag(null, "communityDeterminations");
                                started = true;
                            }
                            serializer.startTag(null, "communityDetermination");
                            serializer.attribute(null, "id", "communityDetermination_" + _id);
                            serializer.startTag(null, "plotObservationID");
                            serializer.text("plotObservation_" + _id);
                            serializer.endTag(null, "plotObservationID");
                            startedRelation = true;
                        }
                        serializer.startTag(null, "communityRelationshipAssertion");
                        serializer.startTag(null, "communityConceptID");
                        serializer.text("communityConcept_" + _id + "_" + communityConcepts[i][1]);
                        serializer.endTag(null, "communityConceptID");
                        serializer.endTag(null, "communityRelationshipAssertion");
                    }
                }
                if (startedRelation) {
                    serializer.endTag(null, "communityDetermination");
                }
            }
        } finally {
            plotCursor.close();
        }
        if (started) {
            serializer.endTag(null, "communityDeterminations");
        }
    }


    //************************************************************//
    //********************** project *****************************//
    //************************************************************//

    // start projects
    private void writeProject(XmlSerializer serializer) throws IOException {
        serializer.startTag(null, "projects");
        serializer.startTag(null, "project");
        serializer.attribute(null, "id", Integer.toString(MainActivity.exportedProjectId));

        serializer.startTag(null, "title");
        serializer.text(projectName);
        serializer.endTag(null, "title");

        serializer.endTag(null, "project");
        serializer.endTag(null, "projects");
    }

    //************************************************************//
    //********************** plots  ******************************//
    //************************************************************//


    // start plots
    private void writePlots(XmlSerializer serializer) throws IOException {

        //cursors
        Cursor plotCursor = db.rawQuery("Select * FROM plot WHERE project_id =  \"" + MainActivity.exportedProjectId + "\"", null);
        String permanent_plot, plot_name, plot_name_output, _id, value;
        boolean started = false;

        //plot loop
        try {
            while (plotCursor.moveToNext()) {
                //plot attribute loop
                if (!started) {
                    serializer.startTag(null, "plots");
                    started = true;
                }
                // setup plot
                serializer.startTag(null, "plot");

                _id = plotCursor.getString(plotCursor.getColumnIndex("_id"));
                serializer.attribute(null, "id", _id);

                plot_name_output = "";
                plot_name = plotCursor.getString(plotCursor.getColumnIndex("name"));
                if (plot_name != null && !plot_name.isEmpty()) {
                    plot_name_output = plot_name;
                    permanent_plot = plotCursor.getString(plotCursor.getColumnIndex("permanent_plot_id"));
                    if (permanent_plot != null && !permanent_plot.isEmpty()) {
                        plot_name_output += "; Permanent plot name: " + permanent_plot;
                    }
                } else {
                    plot_name_output = _id;
                }
                serializer.startTag(null, "plotName");
                serializer.text(plot_name_output);
                serializer.endTag(null, "plotName");

                value = plotCursor.getString(plotCursor.getColumnIndex("parent_plot"));
                if (value != null && !value.isEmpty()) {
                    serializer.startTag(null, "relatedPlot");
                    serializer.startTag(null, "relatedPlotID");
                    serializer.text(value);
                    serializer.endTag(null, "relatedPlotID");
                    serializer.startTag(null, "plotRelationship");
                    serializer.text("higher-level plot");
                    serializer.endTag(null, "plotRelationship");
                    serializer.endTag(null, "relatedPlot");
                }

                value = plotCursor.getString(plotCursor.getColumnIndex("sampling_scheme"));
                serializer.startTag(null, "placementMethod");
                if (value != null) {
                    serializer.text(value);
                }
                serializer.endTag(null, "placementMethod");

                locationPlotEntries(serializer, plotCursor);
                geometryPlotEntries(serializer, plotCursor);
                topographyPlotEntries(serializer, plotCursor);

                serializer.startTag(null, "parentMaterial");
                serializer.startTag(null, "value");
                value = plotCursor.getString(plotCursor.getColumnIndex("rock_type"));
                if (value != null) {
                    serializer.text(value);
                }
                serializer.endTag(null, "value");
                serializer.endTag(null, "parentMaterial");

                simpleUserDefinedEntries(serializer, plotCursor, location, scope_plots);
                simpleUserDefinedEntries(serializer, plotCursor, plot_geometry, scope_plots);
                simpleUserDefinedEntries(serializer, plotCursor, placement_strategy, scope_plots);

                // end plot
                serializer.endTag(null, "plot");
            }
        } finally {
            plotCursor.close();
        }
        if (started) {
            serializer.endTag(null, "plots");
        }
    }

    private void simpleUserDefinedEntries(XmlSerializer serializer, Cursor cursor, String type, String scope) throws IOException {
        Cursor custom_fields_cursor = db.rawQuery("select * from custom_fields where type = \"" + type + "\" and scope = \"" + scope + "\" and profile = \"" + profileId + "\"", null);
        if (custom_fields_cursor.moveToFirst()) {
            try {
                do {
                    String s = custom_fields_cursor
                            .getString(custom_fields_cursor
                                    .getColumnIndex("name")) + (scope.equals("individuals") || scope.equals("species") ? "" : "_" + scope);
                    int i = cursor.getColumnIndex(s);
                    String v = cursor.getString(i);
                    serializer.startTag(null, "simpleUserDefined");
                    if (v != null && !v.isEmpty()) {
                        serializer.startTag(null, "name");
                        serializer.text(custom_fields_cursor.getString(custom_fields_cursor.getColumnIndex("attribute")));
                        serializer.endTag(null, "name");
                        serializer.startTag(null, "value");
                        serializer.text(v);
                        serializer.endTag(null, "value");
                        serializer.startTag(null, "attributeID");
                        serializer.text(custom_fields_cursor
                                .getString(custom_fields_cursor
                                        .getColumnIndex("attribute")) + "_" + scope);
                        serializer.endTag(null, "attributeID");
                    }
                    serializer.endTag(null, "simpleUserDefined");
                } while (custom_fields_cursor.moveToNext());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                custom_fields_cursor.close();
            }
        }
    }

    private void writeObservationNote(XmlSerializer serializer, Cursor cursor, String type, String scope) throws IOException {
        Cursor custom_fields_cursor = db.rawQuery("select * from custom_fields where type = \"" + type + "\" and scope = \"" + scope + "\" and profile = \"" + profileId + "\"", null);
        if (custom_fields_cursor.moveToFirst()) {
            try {
                do {
                    String s = custom_fields_cursor
                            .getString(custom_fields_cursor
                                    .getColumnIndex("name")) + (scope.equals("individuals") || scope.equals("species") ? "" : "_" + scope);
                    int i = cursor.getColumnIndex(s);
                    String v = cursor.getString(i);
                    serializer.startTag(null, "observationNote");
                    if (v != null && !v.isEmpty()) {
                        serializer.text(v);
                    }
                    serializer.endTag(null, "observationNote");
                } while (custom_fields_cursor.moveToNext());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                custom_fields_cursor.close();
            }
        }
    }

    // write location plot entry
    private void locationPlotEntries(XmlSerializer serializer, Cursor cursor) throws IOException {
        String value;

        String _id = cursor.getString(cursor.getColumnIndex("_id"));
        String spatial_reference = cursor.getString(cursor.getColumnIndex("spatial_reference"));
        String coord_units = cursor.getString(cursor.getColumnIndex("coord_system_zone"));

        serializer.startTag(null, "location");
        // latitude/longitude coordinates
        serializer.startTag(null, "horizontalCoordinates");
        serializer.startTag(null, "coordinates");
        serializer.startTag(null, "valueX");
        value = cursor.getString(cursor.getColumnIndex("easting_lon"));
        if (value != null) {
            serializer.text(value);
        }
        serializer.endTag(null, "valueX");
        serializer.startTag(null, "valueY");
        value = cursor.getString(cursor.getColumnIndex("northing_lat"));
        if (value != null) {
            serializer.text(value);
        }
        serializer.endTag(null, "valueY");
        serializer.startTag(null, "attributeID");
        if (spatial_reference != null && spatialReferenceLookup.containsKey(spatial_reference)) {
            serializer.text("geographic_latitude_longitude_in_decimal_degrees_" + spatialReferenceLookup.get(spatial_reference));
        } else {
            serializer.text("geographic_latitude_longitude_in_decimal_degrees");
        }
        serializer.endTag(null, "attributeID");
        serializer.startTag(null, "spatialReference");
        if (spatial_reference != null && !spatial_reference.isEmpty()) {
            serializer.text(spatial_reference);
        }
        serializer.endTag(null, "spatialReference");
        horizontalCoordinatesLocationAccuracy(serializer, cursor);
        serializer.endTag(null, "coordinates");
        serializer.endTag(null, "horizontalCoordinates");

        // utm coordinates
        serializer.startTag(null, "horizontalCoordinates");
        serializer.startTag(null, "coordinates");
        serializer.startTag(null, "valueX");
        value = cursor.getString(cursor.getColumnIndex("utm_easting"));
        if (value != null) {
            serializer.text(value);
        }
        serializer.endTag(null, "valueX");
        serializer.startTag(null, "valueY");
        value = cursor.getString(cursor.getColumnIndex("utm_northing"));
        if (value != null) {
            serializer.text(value);
        }
        serializer.endTag(null, "valueY");
        serializer.startTag(null, "attributeID");
        if (spatial_reference != null && spatialReferenceLookup.containsKey(spatial_reference)) {
            serializer.text("universal_transverse_mercator_" + spatialReferenceLookup.get(spatial_reference));
        } else {
            serializer.text("universal_transverse_mercator");
        }
        serializer.endTag(null, "attributeID");
        serializer.startTag(null, "spatialReference");
        value = cursor.getString(cursor.getColumnIndex("utm_zone"));
        if (value != null && !value.isEmpty()) {
            if (spatial_reference != null && !spatial_reference.isEmpty()) {
                serializer.text(spatial_reference);
            } else {
                serializer.text("WGS84 / UTM zone " + value);
            }
        }
        serializer.endTag(null, "spatialReference");
        horizontalCoordinatesLocationAccuracy(serializer, cursor);
        serializer.endTag(null, "coordinates");
        serializer.endTag(null, "horizontalCoordinates");

        // northing easting coord system
        serializer.startTag(null, "horizontalCoordinates");
        serializer.startTag(null, "coordinates");
        serializer.startTag(null, "valueX");
        value = cursor.getString(cursor.getColumnIndex("easting"));
        if (value != null) {
            serializer.text(value);
        }
        serializer.endTag(null, "valueX");
        serializer.startTag(null, "valueY");
        value = cursor.getString(cursor.getColumnIndex("northing"));
        if (value != null) {
            serializer.text(value);
        }
        serializer.endTag(null, "valueY");
        serializer.startTag(null, "attributeID");
        CoordSpatialPair coordSpatialPair = new CoordSpatialPair(coord_units, spatial_reference);
        if (coordinateUnitsLookup.containsKey(coordSpatialPair)) {
            serializer.text("coordinate_units_" + coordinateUnitsLookup.get(coordSpatialPair));
        }
        serializer.endTag(null, "attributeID");
        serializer.startTag(null, "spatialReference");
        if (spatial_reference != null && !spatial_reference.isEmpty()) {
            serializer.text(spatial_reference);
        }
        serializer.endTag(null, "spatialReference");
        horizontalCoordinatesLocationAccuracy(serializer, cursor);
        serializer.endTag(null, "coordinates");
        serializer.endTag(null, "horizontalCoordinates");


        // vertical coordinates
        value = cursor.getString(cursor.getColumnIndex("altitude"));
        if (value != null && !value.isEmpty()) {
            serializer.startTag(null, "verticalCoordinates");
            serializer.startTag(null, "elevation");
            serializer.startTag(null, "value");
            serializer.text(value);
            serializer.endTag(null, "value");
            serializer.startTag(null, "attributeID");
            serializer.text("elevation_above_sea_level_in_meter");
            serializer.endTag(null, "attributeID");
            serializer.endTag(null, "elevation");
            serializer.endTag(null, "verticalCoordinates");
        }

        // markers
        serializer.startTag(null, "markers");
        value = cursor.getString(cursor.getColumnIndex("marker"));
        if (value != null) {
            serializer.text(value);
        }
        serializer.endTag(null, "markers");

        // grid position
        serializer.startTag(null, "gridPosition");
        serializer.startTag(null, "gridSystem");
        value = cursor.getString(cursor.getColumnIndex("survey_grid"));
        if (value != null) {
            serializer.text(value);
        }
        serializer.endTag(null, "gridSystem");
        serializer.startTag(null, "gridCell");
        value = cursor.getString(cursor.getColumnIndex("survey_cell"));
        if (value != null) {
            serializer.text(value);
        }
        serializer.endTag(null, "gridCell");
        serializer.startTag(null, "gridCellQuadrant");
        value = cursor.getString(cursor.getColumnIndex("survey_quadrant"));
        if (value != null) {
            serializer.text(value);
        }
        serializer.endTag(null, "gridCellQuadrant");
        serializer.endTag(null, "gridPosition");

        // places
        serializer.startTag(null, "places");
        serializer.startTag(null, "placeName");
        value = cursor.getString(cursor.getColumnIndex("place_name"));
        if (value != null) {
            serializer.text(value);
        }
        serializer.endTag(null, "placeName");
        serializer.startTag(null, "placeType");
        serializer.text("Place name");
        serializer.endTag(null, "placeType");
        serializer.endTag(null, "places");

        serializer.startTag(null, "places");
        serializer.startTag(null, "placeName");
        value = cursor.getString(cursor.getColumnIndex("country"));
        if (value != null) {
            serializer.text(value);
        }
        serializer.endTag(null, "placeName");
        serializer.startTag(null, "placeType");
        serializer.text("Country");
        serializer.endTag(null, "placeType");

        serializer.endTag(null, "places");

        serializer.startTag(null, "places");
        serializer.startTag(null, "placeName");
        value = cursor.getString(cursor.getColumnIndex("federal_state"));
        if (value != null) {
            serializer.text(value);
        }
        serializer.endTag(null, "placeName");
        serializer.startTag(null, "placeType");
        serializer.text("Federated State");
        serializer.endTag(null, "placeType");

        serializer.endTag(null, "places");

        serializer.startTag(null, "places");
        serializer.startTag(null, "placeName");
        value = cursor.getString(cursor.getColumnIndex("county"));
        if (value != null) {
            serializer.text(value);
        }
        serializer.endTag(null, "placeName");
        serializer.startTag(null, "placeType");
        serializer.text("County");
        serializer.endTag(null, "placeType");

        serializer.endTag(null, "places");

        // finish location
        serializer.endTag(null, "location");
    }

    private void horizontalCoordinatesLocationAccuracy(XmlSerializer serializer, Cursor cursor) throws IOException {
        String value;
        // meter
        serializer.startTag(null, "locationAccuracy");
        serializer.startTag(null, "value");
        value = cursor.getString(cursor.getColumnIndex("accuracy"));
        if (value != null) {
            serializer.text(value);
        }
        serializer.endTag(null, "value");
        serializer.startTag(null, "attributeID");
        serializer.text("location_accuracy_in_meter");
        serializer.endTag(null, "attributeID");
        serializer.endTag(null, "locationAccuracy");
        // dop
        serializer.startTag(null, "locationAccuracy");
        serializer.startTag(null, "value");
        value = cursor.getString(cursor.getColumnIndex("dop"));
        if (value != null) {
            serializer.text(value);
        }
        serializer.endTag(null, "value");
        serializer.startTag(null, "attributeID");
        serializer.text("location_accuracy_in_dop");
        serializer.endTag(null, "attributeID");
        serializer.endTag(null, "locationAccuracy");
    }

    // write geometry plot entry
    private void geometryPlotEntries(XmlSerializer serializer, Cursor cursor) throws IOException {
        String value;

        serializer.startTag(null, "geometry");

        serializer.startTag(null, "area");
        serializer.startTag(null, "value");
        value = cursor.getString(cursor.getColumnIndex("plot_size"));
        if (value != null) {
            serializer.text(value);
        }
        serializer.endTag(null, "value");
        serializer.startTag(null, "attributeID");
        serializer.text("plot_surface_in_square_meter");
        serializer.endTag(null, "attributeID");
        serializer.endTag(null, "area");

        serializer.startTag(null, "shape");
        value = cursor.getString(cursor.getColumnIndex("plot_shape"));
        if (value != null) {
            serializer.text(value);
        }
        serializer.endTag(null, "shape");

        serializer.startTag(null, "plotOrigin");
        serializer.startTag(null, "plotOriginPosition");
        value = cursor.getString(cursor.getColumnIndex("ref_point"));
        if (value != null) {
            serializer.text(value);
        }
        serializer.endTag(null, "plotOriginPosition");
        serializer.endTag(null, "plotOrigin");

        serializer.startTag(null, "radius");
        serializer.startTag(null, "value");
        value = cursor.getString(cursor.getColumnIndex("plot_radius"));
        if (value != null) {
            serializer.text(value);
        }
        serializer.endTag(null, "value");
        serializer.startTag(null, "attributeID");
        serializer.text("plot_radius_in_meter");
        serializer.endTag(null, "attributeID");
        serializer.endTag(null, "radius");

        serializer.startTag(null, "width");
        serializer.startTag(null, "value");
        value = cursor.getString(cursor.getColumnIndex("plot_width"));
        if (value != null) {
            serializer.text(value);
        }
        serializer.endTag(null, "value");
        serializer.startTag(null, "attributeID");
        serializer.text("plot_width_in_meter");
        serializer.endTag(null, "attributeID");
        serializer.endTag(null, "width");

        serializer.startTag(null, "length");
        serializer.startTag(null, "value");
        value = cursor.getString(cursor.getColumnIndex("plot_length"));
        if (value != null) {
            serializer.text(value);
        }
        serializer.endTag(null, "value");
        serializer.startTag(null, "attributeID");
        serializer.text("plot_length_in_meter");
        serializer.endTag(null, "attributeID");
        serializer.endTag(null, "length");

        serializer.startTag(null, "orientation");
        serializer.startTag(null, "value");
        value = cursor.getString(cursor.getColumnIndex("orientation"));
        if (value != null) {
            serializer.text(value);
        }
        serializer.endTag(null, "value");
        serializer.startTag(null, "attributeID");
        serializer.text("plot_orientation_in_degrees");
        serializer.endTag(null, "attributeID");
        serializer.endTag(null, "orientation");

        serializer.endTag(null, "geometry");
    }

    // write topography plot entry
    private void topographyPlotEntries(XmlSerializer serializer, Cursor cursor) throws IOException {
        String value_aspect_deg = "";
        serializer.startTag(null, "topography");
        serializer.startTag(null, "aspect");

        serializer.startTag(null, "value");
        value_aspect_deg = cursor.getString(cursor.getColumnIndex("aspect"));

        String value_aspect_classes = cursor.getString(cursor.getColumnIndex("aspect_classes"));

        if (value_aspect_deg != null && !value_aspect_deg.isEmpty() ) {
            serializer.text(value_aspect_deg);
        }else if (value_aspect_classes != null && !value_aspect_classes.isEmpty()) {
            serializer.text(value_aspect_classes);
        }
        serializer.endTag(null, "value");


        serializer.startTag(null, "attributeID");


        if (value_aspect_deg != null && !value_aspect_deg.isEmpty() ) {
            serializer.text("aspect_in_degrees");
        }else if (value_aspect_classes != null && !value_aspect_classes.isEmpty()) {
            serializer.text("aspect_class_"+value_aspect_classes);
        }



        serializer.endTag(null, "attributeID");

        /*
        serializer.startTag(null, "value");
        value = cursor.getString(cursor.getColumnIndex("aspect_classes"));
        if (value != null) {
            serializer.text(value);
        }
        serializer.endTag(null, "value");

         */

        serializer.endTag(null, "aspect");

        String value = "";

        serializer.startTag(null, "slope");
        serializer.startTag(null, "value");
        value = cursor.getString(cursor.getColumnIndex("slope"));
        if (value != null) {
            serializer.text(value);
        }
        serializer.endTag(null, "value");
        serializer.startTag(null, "attributeID");
        serializer.text("slope_in_degrees");
        serializer.endTag(null, "attributeID");
        serializer.endTag(null, "slope");

        serializer.startTag(null, "landform");
        value = cursor.getString(cursor.getColumnIndex("landform"));
        if (value != null) {
            serializer.text(value);
        }
        serializer.endTag(null, "landform");
        serializer.endTag(null, "topography");
    }

    //************************************************************//
    //********************** individual organisms *******************//
    //************************************************************//
    private void writeIndividualOrganisms(XmlSerializer serializer) throws IOException {
        //cursors
        Cursor plotCursor = db.rawQuery("Select * FROM plot WHERE project_id =  \"" + MainActivity.exportedProjectId + "\"", null);
        String _id;
        boolean started = false;
        try {
            while (plotCursor.moveToNext()) {
                _id = plotCursor.getString(plotCursor.getColumnIndex("_id"));
                Cursor individualsCursor = db.rawQuery("Select * FROM individuals WHERE plot_id =  \"" + _id + "\"", null);
                try {
                    while (individualsCursor.moveToNext()) {
                        String indiviualId = individualsCursor.getString(individualsCursor.getColumnIndex("_id"));
                        if (indiviualId != null && !indiviualId.isEmpty()) {
                            if (!started) {
                                serializer.startTag(null, "individualOrganisms");
                                started = true;
                            }
                            String protocol = individualsCursor.getString(individualsCursor.getColumnIndex("protocol"));
                            serializer.startTag(null, "individualOrganism");
                            serializer.attribute(null, "id", "individualOrganism_" + indiviualId);
                            serializer.startTag(null, "plotID");
                            serializer.text(_id);
                            serializer.endTag(null, "plotID");
                            serializer.startTag(null, "individualOrganismLabel");
                            serializer.text(indiviualId);
                            serializer.endTag(null, "individualOrganismLabel");
                            serializer.startTag(null, "organismIdentityID");
                            String speciesNr = individualsCursor.getString(individualsCursor.getColumnIndex("plot_id"));
                            if (speciesNr != null) {
                                serializer.text("organismIdentitiy_" + indiviualId);
                            }
                            serializer.endTag(null, "organismIdentityID");

                            /* TODO: I think we need latitude, longitude and accuracy here rather than x and y
                             *   but x and y coords should also be exported if available */
                            String x_coord = individualsCursor.getString(individualsCursor.getColumnIndex("x_coord"));
                            String y_coord = individualsCursor.getString(individualsCursor.getColumnIndex("y_coord"));

                            String direction_origin = individualsCursor.getString(individualsCursor.getColumnIndex("direction_origin"));
                            String distance_origin = individualsCursor.getString(individualsCursor.getColumnIndex("distance_origin"));
                            String quarter = individualsCursor.getString(individualsCursor.getColumnIndex("quarter"));

                            String longitude = individualsCursor.getString(individualsCursor.getColumnIndex("longitude"));
                            String latitude = individualsCursor.getString(individualsCursor.getColumnIndex("latitude"));
                            String accuracy = individualsCursor.getString(individualsCursor.getColumnIndex("accuracy"));


                            serializer.startTag(null, "location");

                            if (x_coord != null && !x_coord.isEmpty()
                                    || y_coord != null && !y_coord.isEmpty()
                                    || direction_origin != null && !direction_origin.isEmpty()
                                    || distance_origin != null && !distance_origin.isEmpty()
                                    || longitude != null && !longitude.isEmpty()
                                    || latitude != null && !latitude.isEmpty()
                                    || accuracy != null && !accuracy.isEmpty()) {
                                /* TODO: change tag-names (same as in Plots?) and also add tag for lat - lon with:
                                 *   - add AttributeID depending on spatial reference */
                                /*  - add Spatial reference if available */
                                /*  - Accuracy */
                                serializer.startTag(null, "horizontalLocation");

                                if (longitude != null && !longitude.isEmpty() || latitude != null && !latitude.isEmpty() || accuracy != null && !accuracy.isEmpty()) {
                                    serializer.startTag(null, "xyCoordinates");
                                    if (longitude != null && !longitude.isEmpty() || latitude != null && !latitude.isEmpty()) {

                                        if (longitude != null && !longitude.isEmpty()) {
                                            serializer.startTag(null, "valueX");
                                            serializer.text(longitude);
                                            serializer.endTag(null, "valueX");
                                        }
                                        if (latitude != null && !latitude.isEmpty()) {
                                            serializer.startTag(null, "valueY");
                                            serializer.text(latitude);
                                            serializer.endTag(null, "valueY");
                                        }

                                        serializer.startTag(null, "attributeID");
                                        serializer.text("geographic_latitude_longitude_in_decimal_degrees");
                                        serializer.endTag(null, "attributeID");

                                        serializer.startTag(null, "spatialReference");
                                        serializer.text("EPSG:4326");
                                        serializer.endTag(null, "spatialReference");

                                    }
                                    if (accuracy != null && !accuracy.isEmpty()) {
                                        serializer.startTag(null, "locationAccuracy");
                                        serializer.startTag(null, "value");
                                        serializer.text(accuracy);
                                        serializer.endTag(null, "value");
                                        serializer.startTag(null, "attributeID");
                                        serializer.text("location_accuracy_in_meter");
                                        serializer.endTag(null, "attributeID");
                                        serializer.endTag(null, "locationAccuracy");
                                    }
                                    serializer.endTag(null, "xyCoordinates");


                                }

                                // polar coords
                                if (direction_origin != null && !direction_origin.isEmpty() || distance_origin != null && !distance_origin.isEmpty()) {
                                    serializer.startTag(null, "polarCoordinates");
                                    if (direction_origin != null && !direction_origin.isEmpty()) {
                                        serializer.startTag(null, "direction");
                                        serializer.startTag(null, "value");
                                        serializer.text(direction_origin);
                                        serializer.endTag(null, "value");
                                        serializer.startTag(null, "attributeID");
                                        serializer.text("polar_coordinate_direction_" + polarCoordinateDirectionLookup.get(protocol));
                                        serializer.endTag(null, "attributeID");
                                        serializer.endTag(null, "direction");
                                    }
                                    if (distance_origin != null && !distance_origin.isEmpty()) {
                                        serializer.startTag(null, "distance");
                                        serializer.startTag(null, "value");
                                        serializer.text(distance_origin);
                                        serializer.endTag(null, "value");
                                        serializer.startTag(null, "attributeID");
                                        serializer.text("polar_coordinate_distance_" + polarCoordinateDistanceLookup.get(protocol));
                                        serializer.endTag(null, "attributeID");
                                        serializer.endTag(null, "distance");
                                    }
                                    serializer.endTag(null, "polarCoordinates");
                                }
                                serializer.endTag(null, "horizontalLocation");
                            }

                            if (quarter != null && !quarter.isEmpty()) {
                                serializer.startTag(null, "quadrant");
                                serializer.startTag(null, "value");
                                serializer.text(quarter);
                                serializer.endTag(null, "value");
                                serializer.startTag(null, "attributeID");
                                serializer.text("individual_location_quarter_" + individualLocationQuarterLookup.get(protocol) + "_" + quarter);
                                serializer.endTag(null, "attributeID");
                                serializer.endTag(null, "quadrant");
                            }

                            serializer.endTag(null, "location");

                            serializer.endTag(null, "individualOrganism");
                        }
                    }
                } finally {
                    individualsCursor.close();
                }
            }
        } finally {
            plotCursor.close();
        }
        if (started) {
            serializer.endTag(null, "individualOrganisms");
        }

    }

    //************************************************************//
    //********************** plot observations *******************//
    //************************************************************//

    private void writePlotObversations(XmlSerializer serializer) throws IOException {
        //cursors
        Cursor plotCursor = db.rawQuery("Select * FROM plot WHERE project_id =  \"" + MainActivity.exportedProjectId + "\"", null);
        String _id, value;
        boolean started = false;

        //plot observations loop
        try {
            while (plotCursor.moveToNext()) {
                if (!started) {
                    serializer.startTag(null, "plotObservations");
                    started = true;
                }
                // setup
                serializer.startTag(null, "plotObservation");
                _id = plotCursor.getString(plotCursor.getColumnIndex("_id"));
                serializer.attribute(null, "id", "plotObservation_" + _id);
                serializer.startTag(null, "plotID");
                serializer.text(_id);
                serializer.endTag(null, "plotID");
                serializer.startTag(null, "obsStartDate");
                value = plotCursor.getString(plotCursor.getColumnIndex("date"));
                serializer.text(ddmmyyyToyyyymmdd(value));
                serializer.endTag(null, "obsStartDate");
                serializer.startTag(null, "projectID");
                serializer.text(projectId);
                serializer.endTag(null, "projectID");
                serializer.startTag(null, "communityObservationID");
                serializer.text("communityObservation_" + _id);
                serializer.endTag(null, "communityObservationID");
                serializer.startTag(null, "siteObservationID");
                serializer.text("siteObservation_" + _id);
                serializer.endTag(null, "siteObservationID");
                if (plotObservatorIds.containsKey(_id)) {
                    List<String> observationParties = plotObservatorIds.get(_id);
                    for (String partyId : observationParties) {
                        serializer.startTag(null, "observationPartyID");
                        serializer.text(getOriginatorIDFromParties(partyId));
                        serializer.endTag(null, "observationPartyID");
                    }
                }
                String license = plotCursor.getString(plotCursor.getColumnIndex("license"));
                String attribution = plotCursor.getString(plotCursor.getColumnIndex("attribution"));
                if (license != null && !license.isEmpty() || attribution != null && !attribution.isEmpty() || getOriginatorIDFromDataOwner(String.valueOf(profileId))) {
                    serializer.startTag(null, "license");
                    if (license != null && !license.isEmpty()) {
                        serializer.startTag(null, "licenseName");
                        serializer.text(license);
                        serializer.endTag(null, "licenseName");
                    }
                    if (attribution != null && !attribution.isEmpty()) {
                        serializer.startTag(null, "attribution");
                        serializer.text(attribution);
                        serializer.endTag(null, "attribution");
                    }

                    if (getOriginatorIDFromDataOwner(String.valueOf(profileId))) {
                        serializer.startTag(null, "partyID");
                        serializer.text("owner_" + profileId);
                        serializer.endTag(null, "partyID");
                    }
                    serializer.endTag(null, "license");
                }
                serializer.startTag(null, "taxonomicQuality");
                serializer.startTag(null, "qualityAssessment");
                value = plotCursor.getString(plotCursor.getColumnIndex("mosses_ident"));
                if (value != null) {
                    serializer.text(value);
                }
                serializer.endTag(null, "qualityAssessment");
                serializer.startTag(null, "assessmentType");
                serializer.text("Mosses identified (true or false)");
                serializer.endTag(null, "assessmentType");
                serializer.endTag(null, "taxonomicQuality");

                serializer.startTag(null, "taxonomicQuality");
                serializer.startTag(null, "qualityAssessment");
                value = plotCursor.getString(plotCursor.getColumnIndex("lichens_ident"));
                if (value != null) {
                    serializer.text(value);
                }
                serializer.endTag(null, "qualityAssessment");
                serializer.startTag(null, "assessmentType");
                serializer.text("Lichens identified (true or false)");
                serializer.endTag(null, "assessmentType");
                serializer.endTag(null, "taxonomicQuality");

                serializer.startTag(null, "observationNarrative");
                value = plotCursor.getString(plotCursor.getColumnIndex("remarks"));
                if (value != null) {
                    serializer.text(value);
                }
                serializer.endTag(null, "observationNarrative");

                serializer.startTag(null, "referencePublication");
                serializer.startTag(null, "citationID");
                serializer.text("citation_" + _id);
                serializer.endTag(null, "citationID");
                serializer.startTag(null, "referenceTable");
                value = plotCursor.getString(plotCursor.getColumnIndex("source_table"));
                if (value != null) {
                    serializer.text(value);
                }
                serializer.endTag(null, "referenceTable");
                serializer.startTag(null, "referencePlot");
                value = plotCursor.getString(plotCursor.getColumnIndex("source_plot"));
                if (value != null) {
                    serializer.text(value);
                }
                serializer.endTag(null, "referencePlot");
                serializer.endTag(null, "referencePublication");

                writeObservationNote(serializer, plotCursor, "Remarks", scope_plots);

                simpleUserDefinedEntries(serializer, plotCursor, "Custom", scope_plots);
                //writeObservationNote(serializer, plotCursor, "Remarks", scope_plots);
                simpleUserDefinedEntries(serializer, plotCursor, meta_data, scope_plots);
                simpleUserDefinedEntries(serializer, plotCursor, legal, scope_plots);
                simpleUserDefinedEntries(serializer, plotCursor, literature_source, scope_plots);


                serializer.endTag(null, "plotObservation");

            }
        } finally {
            plotCursor.close();
        }
        if (started) {
            serializer.endTag(null, "plotObservations");
        }
    }

    //************************************************************//
    //********* individualOrganismObservations***************//
    //************************************************************//

    private void writeIndividualOrganismObservations(XmlSerializer serializer) throws IOException {
        //cursors
        Cursor plotCursor = db.rawQuery("Select * FROM plot WHERE project_id =  \"" + MainActivity.exportedProjectId + "\"", null);
        String _id;
        boolean started = false;
        try {
            while (plotCursor.moveToNext()) {
                _id = plotCursor.getString(plotCursor.getColumnIndex("_id"));
                Cursor individualsCursor = db.rawQuery("Select * FROM individuals WHERE plot_id =  \"" + _id + "\"", null);
                try {
                    while (individualsCursor.moveToNext()) {
                        String indiviualId = individualsCursor.getString(individualsCursor.getColumnIndex("_id"));
                        if (indiviualId != null && !indiviualId.isEmpty()) {
                            if (!started) {
                                serializer.startTag(null, "individualOrganismObservations");
                                started = true;
                            }
                            serializer.startTag(null, "individualOrganismObservation");
                            serializer.attribute(null, "id", "individualOrganismObservation_" + indiviualId);
                            serializer.startTag(null, "plotObservationID");
                            serializer.text("plotObservation_" + _id);
                            serializer.endTag(null, "plotObservationID");
                            serializer.startTag(null, "individualOrganismID");
                            serializer.text("individualOrganism_" + indiviualId);
                            serializer.endTag(null, "individualOrganismID");

                            String dbh = individualsCursor.getString(individualsCursor.getColumnIndex("dbh"));
                            String dbh_above_ground = individualsCursor.getString(individualsCursor.getColumnIndex("dbh_above_ground"));
                            if (dbh != null && !dbh.isEmpty()) {
                                serializer.startTag(null, "individualOrganismMeasurement");
                                serializer.startTag(null, "value");
                                serializer.text(dbh);
                                serializer.endTag(null, "value");
                                serializer.startTag(null, "attributeID");
                                if (dbh_above_ground == null || dbh_above_ground.isEmpty()) {
                                    serializer.text("diameter_at_breast_height_in_cm_unspecified");
                                } else {
                                    serializer.text("diameter_at_breast_height_in_cm_" + dbh_above_ground);
                                }
                                serializer.endTag(null, "attributeID");
                                serializer.endTag(null, "individualOrganismMeasurement");
                            }
                            String girth = individualsCursor.getString(individualsCursor.getColumnIndex("girth"));
                            String girth_above_ground = individualsCursor.getString(individualsCursor.getColumnIndex("girth_above_ground"));
                            if (girth != null && !girth.isEmpty()) {
                                serializer.startTag(null, "individualOrganismMeasurement");
                                serializer.startTag(null, "value");
                                serializer.text(girth);
                                serializer.endTag(null, "value");
                                serializer.startTag(null, "attributeID");
                                if (girth_above_ground == null || girth_above_ground.isEmpty()) {
                                    serializer.text("girth_at_breast_height_in_cm_unspecified");
                                } else {
                                    serializer.text("girth_at_breast_height_in_cm_" + girth_above_ground);
                                }
                                serializer.endTag(null, "attributeID");
                                serializer.endTag(null, "individualOrganismMeasurement");
                            }

                            /*String custom_a = individualsCursor.getString(individualsCursor.getColumnIndex("custom_a"));
                            if (custom_a != null && !custom_a.isEmpty()) {
                                serializer.startTag(null, "complexUserDefined");
                                serializer.startTag(null, "name");
                                serializer.text("Custom attribute A");
                                serializer.endTag(null, "name");
                                serializer.startTag(null, "value");
                                serializer.text(custom_a);
                                serializer.endTag(null, "value");
                                serializer.startTag(null, "attributeID");
                                serializer.endTag(null, "attributeID");
                                serializer.endTag(null, "complexUserDefined");
                            }

                            String custom_b = individualsCursor.getString(individualsCursor.getColumnIndex("custom_b"));
                            if (custom_b != null && !custom_b.isEmpty()) {
                                serializer.startTag(null, "simpleUserDefined");
                                serializer.startTag(null, "name");
                                serializer.text("Custom attribute B");
                                serializer.endTag(null, "name");
                                serializer.startTag(null, "value");
                                serializer.text(custom_b);
                                serializer.endTag(null, "value");
                                serializer.startTag(null, "methodID");
                                serializer.endTag(null, "methodID");
                                serializer.endTag(null, "simpleUserDefined");
                            }*/

                            simpleUserDefinedEntries(serializer, individualsCursor, custom, scope_individuals);

                            serializer.endTag(null, "individualOrganismObservation");
                        }
                    }
                } finally {
                    individualsCursor.close();
                }
            }
        } finally {
            plotCursor.close();
        }
        if (started) {
            serializer.endTag(null, "individualOrganismObservations");
        }
    }

    //************************************************************//
    //*********** aggregateOrganismObservation  ******************//
    //************************************************************//

    // String[i][0] text of name tag
    // String[i][1] database column for value tag
    private static final String[][] simpleUserDefinedAggregateOrganismObservationEntries = new String[][]{
            {"Sociability", "sociability"},
            {"Vitality", "vitality"},
            {"Additional information related to this species observation A", "custom_a"},
            {"Additional information related to this species observation B", "custom_b"}
    };

    private void writeAggregateOrganismObservations(XmlSerializer serializer) throws IOException {
        //cursors
        Cursor plotCursor = db.rawQuery("Select * FROM plot WHERE project_id =  \"" + MainActivity.exportedProjectId + "\"", null);
        String _id, speciesId, value;
        boolean noQuantityInProject = true;
        boolean observationsAvailable = false;
        //serializer.startTag(null, "aggregateOrganismObservations");

        try {
            while (plotCursor.moveToNext()) {
                _id = plotCursor.getString(plotCursor.getColumnIndex("_id"));
                Cursor speciesCursor = db.rawQuery("Select * FROM species WHERE plot_id =  \"" + _id + "\"", null);
                if (speciesCursor.getCount() > 0) {
                    observationsAvailable = true;
                }
                //is this right here?
                speciesCursor.close();
            }

        } finally {
            plotCursor.close();

        }

        if (observationsAvailable) {
            serializer.startTag(null, "aggregateOrganismObservations");
        }

        //Re-Initialise Plot-Cursor
        plotCursor = db.rawQuery("Select * FROM plot WHERE project_id =  \"" + MainActivity.exportedProjectId + "\"", null);

        try {
            while (plotCursor.moveToNext()) {
                _id = plotCursor.getString(plotCursor.getColumnIndex("_id"));
                Cursor speciesCursor = db.rawQuery("Select * FROM species WHERE plot_id =  \"" + _id + "\"", null);
                if (speciesCursor.getCount() > 0) {
                    //serializer.startTag(null, "aggregateOrganismObservations");
                }
                try {
                    while (speciesCursor.moveToNext()) {
                        speciesId = speciesCursor.getString(speciesCursor.getColumnIndex("_id"));
                        if (speciesId != null && !speciesId.isEmpty()) {
                            serializer.startTag(null, "aggregateOrganismObservation");
                            serializer.attribute(null, "id", "aggregateObservation_" + speciesId);
                            serializer.startTag(null, "plotObservationID");
                            serializer.text("plotObservation_" + speciesCursor.getString(speciesCursor.getColumnIndex("plot_id")));
                            serializer.endTag(null, "plotObservationID");
                            serializer.startTag(null, "organismIdentityID");
                            serializer.text("organismIdentitiy_" + speciesId);
                            serializer.endTag(null, "organismIdentityID");

                            serializer.startTag(null, "aggregateOrganismMeasurement");
                            /* DONE: write a value even if it's null */
                            /* DONE: Link attributeID even if value is null */
                            value = speciesCursor.getString(speciesCursor.getColumnIndex("quantity"));

                            serializer.startTag(null, "value");
                            serializer.text(value != null && !value.isEmpty() ? value : "NA");
                            serializer.endTag(null, "value");
                            serializer.startTag(null, "attributeID");

                            if (value != null && !value.isEmpty()) {
                                noQuantityInProject = false;
                            }

                            if (coverScale != null) {
                                if (coverScale.equals("'00'")) {
                                    serializer.text("percent_cover_of_species");
                                } else {
                                    if (value != null && !value.isEmpty()) {
                                        serializer.text("attribute_coverscale_" + value.replaceAll("\\s+", ""));
                                    } else {
                                        /* TODO: what should we write for the attributeID if the value is null?
                                         *   for now this will be 00
                                         *   we use 00 because it is free (if it _is_ 00 then the method would be a different one, thus we can assure this number is free */
                                        serializer.text("attribute_coverscale_00");
                                    }
                                }
                            } else {
                                /* TODO: what should be done if no coverscale was selected for the project?
                                 *   for now this will be called coverscale_null*/
                                serializer.text("attribute_coverscale_null");
                            }
                            serializer.endTag(null, "attributeID");
                            serializer.endTag(null, "aggregateOrganismMeasurement");

                            aggregateOrganismObservationGetStratumObservationID(serializer, _id, speciesCursor);

                            //simpleUserDefinedAggregateOrganismObservationEntries(serializer, speciesCursor);
                            simpleUserDefinedEntries(serializer, speciesCursor, custom, scope_species);

                            serializer.endTag(null, "aggregateOrganismObservation");
                        }
                    }
                } finally {
                    if (speciesCursor.getCount() > 0 ) {
                        //serializer.endTag(null, "aggregateOrganismObservations");
                    }
                    speciesCursor.close();
                }
            }
        } finally {
            plotCursor.close();
        }

        //write end tag
        if (observationsAvailable) {
            serializer.endTag(null, "aggregateOrganismObservations");

            if (noQuantityInProject){
                Toast toast;
                toast = Toast.makeText(MainActivity.mContext, R.string.export_project_vegx_toast_d, Toast.LENGTH_SHORT);
                toast.show();
            }
        }


        //serializer.endTag(null, "aggregateOrganismObservations");



    }

    /* DONE: check when this is written (refers to VGK-7)
     *   this works as expected */
    private void aggregateOrganismObservationGetStratumObservationID(XmlSerializer serializer, String plot_id, Cursor speciesCursor) throws IOException {
        String layer_id = speciesCursor.getString(speciesCursor.getColumnIndex("layer_id"));
        Cursor stratumObservationIDcursor = db.rawQuery("SELECT _id FROM plot_layer WHERE plot_id = \"" + plot_id + "\" and plot_layer_id = \"" + layer_id + "\" ", null);
        try {
            while (stratumObservationIDcursor.moveToNext()) {
                String stratum_observation_id = stratumObservationIDcursor.getString(stratumObservationIDcursor.getColumnIndex("_id"));
                if (stratum_observation_id != null && !stratum_observation_id.isEmpty()) {
                    serializer.startTag(null, "stratumObservationID");
                    serializer.text("stratumObservation_" + stratum_observation_id);
                    serializer.endTag(null, "stratumObservationID");
                    //serializer.startTag(null, "stratumObservations");
                    //serializer.text("layer_" + layer_id);
                    //serializer.endTag(null, "stratumObservations");

                }
            }
        } finally {
            stratumObservationIDcursor.close();
        }

    }

    private void simpleUserDefinedAggregateOrganismObservationEntries(XmlSerializer serializer, Cursor cursor) throws IOException {
        String value;
        for (int i = 0; i < simpleUserDefinedAggregateOrganismObservationEntries.length; i++) {
            value = cursor.getString(cursor.getColumnIndex(simpleUserDefinedAggregateOrganismObservationEntries[i][1]));
            if (value != null && !value.isEmpty()) {
                serializer.startTag(null, "simpleUserDefined");
                serializer.startTag(null, "name");
                serializer.text(simpleUserDefinedAggregateOrganismObservationEntries[i][0]);
                serializer.endTag(null, "name");
                serializer.startTag(null, "value");
                if (value != null) {
                    serializer.text(value);
                }
                serializer.endTag(null, "value");
                serializer.startTag(null, "methodID");
                serializer.endTag(null, "methodID");
                serializer.endTag(null, "simpleUserDefined");
            }
        }
    }

    //************************************************************//
    //**************** stratumObservation  ***********************//
    //************************************************************//

    /* DONE: check when this is written and if it works properly (refers to VGK-7)
     *   this works as expected */
    private void writeStratumObversations(XmlSerializer serializer) throws IOException {
        //cursors
        Cursor plotCursor = db.rawQuery("Select * FROM plot WHERE project_id =  \"" + MainActivity.exportedProjectId + "\"", null);
        String _id;
        boolean started = false;
        try {

            while (plotCursor.moveToNext()) {
                _id = plotCursor.getString(plotCursor.getColumnIndex("_id"));
                Cursor plotLayerCursor = db.rawQuery("Select * FROM plot_layer WHERE plot_id =  \"" + _id + "\"", null);
                try {
                    String layerId, value;
                    while (plotLayerCursor.moveToNext()) {
                        layerId = plotLayerCursor.getString(plotLayerCursor.getColumnIndex("_id"));
                        if (layerId != null && !layerId.isEmpty()) {
                            if (!started) {
                                serializer.startTag(null, "stratumObservations");
                                started = true;
                            }
                            serializer.startTag(null, "stratumObservation");
                            serializer.attribute(null, "id", "stratumObservation_" + layerId);
                            serializer.startTag(null, "stratumID");
                            serializer.text("stratum_" + plotLayerCursor.getString(plotLayerCursor.getColumnIndex("plot_layer_id")));
                            serializer.endTag(null, "stratumID");
                            serializer.startTag(null, "plotObservationID");
                            serializer.text("plotObservation_" + plotLayerCursor.getString(plotLayerCursor.getColumnIndex("plot_id")));
                            serializer.endTag(null, "plotObservationID");

                            // max height
                            serializer.startTag(null, "upperLimitMeasurement");
                            /* DONE: write a value even if it's null */
                            /* DONE: Link attributeID even if value is null */
                            value = plotLayerCursor.getString(plotLayerCursor.getColumnIndex("max_height"));

                            serializer.startTag(null, "value");
                            serializer.text(value != null && !value.isEmpty() ? value : "");
                            serializer.endTag(null, "value");
                            serializer.startTag(null, "attributeID");
                            serializer.text("maximum_height_of_layer_in_meter");
                            serializer.endTag(null, "attributeID");

                            serializer.endTag(null, "upperLimitMeasurement");

                            /* DONE: write a value even if it's null */
                            /* DONE: Link attributeID even if value is null */

                            String avg_height = plotLayerCursor.getString(plotLayerCursor.getColumnIndex("avg_height"));
                            String cover = plotLayerCursor.getString(plotLayerCursor.getColumnIndex("cover"));
                            if ((avg_height == null || avg_height.isEmpty()) && (cover == null || cover.isEmpty())) {
                                serializer.startTag(null, "stratumMeasurement");
                                serializer.startTag(null, "value");
                                serializer.text("");
                                serializer.endTag(null, "value");
                                serializer.startTag(null, "attributeID");
                                /* TODO: is it better to have avg_height or cover here? */
                                serializer.text("average_stratum_height_in_meter");
                                serializer.endTag(null, "attributeID");
                                serializer.endTag(null, "stratumMeasurement");
                            } else {
                                // avg height
                                value = plotLayerCursor.getString(plotLayerCursor.getColumnIndex("avg_height"));
                                if (value != null && !value.isEmpty()) {
                                    serializer.startTag(null, "stratumMeasurement");
                                    serializer.startTag(null, "value");
                                    serializer.text(value);
                                    serializer.endTag(null, "value");
                                    serializer.startTag(null, "attributeID");
                                    serializer.text("average_stratum_height_in_meter");
                                    serializer.endTag(null, "attributeID");
                                    serializer.endTag(null, "stratumMeasurement");
                                }
                                // cover
                                value = plotLayerCursor.getString(plotLayerCursor.getColumnIndex("cover"));
                                if (value != null && !value.isEmpty()) {
                                    serializer.startTag(null, "stratumMeasurement");
                                    serializer.startTag(null, "value");
                                    serializer.text(value);
                                    serializer.endTag(null, "value");
                                    serializer.startTag(null, "attributeID");
                                    serializer.text("percent_cover_of_layer_stratum");
                                    serializer.endTag(null, "attributeID");
                                    serializer.endTag(null, "stratumMeasurement");
                                }
                            }

                            serializer.endTag(null, "stratumObservation");
                        }
                    }
                } finally {
                    plotLayerCursor.close();
                }
            }
        } finally {
            plotCursor.close();
        }
        if (started) {
            serializer.endTag(null, "stratumObservations");
        }
    }

    //************************************************************//
    //******************* community observations *****************//
    //************************************************************//

    private void writeCommunityObversations(XmlSerializer serializer) throws IOException {
        //cursors
        Cursor plotCursor = db.rawQuery("Select * FROM plot WHERE project_id =  \"" + MainActivity.exportedProjectId + "\"", null);
        String _id;
        boolean started = false;
        //plot observations loop
        try {
            while (plotCursor.moveToNext()) {
                String succesion_stage = plotCursor.getString(plotCursor.getColumnIndex("succession_stage"));
                String stand_age = plotCursor.getString(plotCursor.getColumnIndex("stand_age"));
                String phenological_state = plotCursor.getString(plotCursor.getColumnIndex("phenological_state"));
                if (!started) {
                    serializer.startTag(null, "communityObservations");
                    started = true;
                }
                // setup
                serializer.startTag(null, "communityObservation");
                _id = plotCursor.getString(plotCursor.getColumnIndex("_id"));
                serializer.attribute(null, "id", "communityObservation_" + _id);
                serializer.startTag(null, "plotObservationID");
                serializer.text("plotObservation_" + _id);
                serializer.endTag(null, "plotObservationID");

                if (succesion_stage != null) {
                    serializer.startTag(null, "successionalType");
                    serializer.startTag(null, "value");
                    serializer.text(succesion_stage);
                    serializer.endTag(null, "value");
                    serializer.startTag(null, "qualitativeAttributeID");
                    serializer.endTag(null, "qualitativeAttributeID");
                    serializer.endTag(null, "successionalType");
                }
                if (stand_age != null) {
                    serializer.startTag(null, "simpleUserDefined");
                    serializer.startTag(null, "name");
                    serializer.text("Stand Age");
                    serializer.endTag(null, "name");
                    serializer.startTag(null, "value");
                    serializer.text(stand_age);
                    serializer.endTag(null, "value");
                    serializer.startTag(null, "methodID");
                    serializer.endTag(null, "methodID");
                    serializer.endTag(null, "simpleUserDefined");
                }
                if (phenological_state != null) {
                    serializer.startTag(null, "simpleUserDefined");
                    serializer.startTag(null, "name");
                    serializer.text("Phenological state");
                    serializer.endTag(null, "name");
                    serializer.startTag(null, "value");
                    serializer.text(phenological_state);
                    serializer.endTag(null, "value");
                    serializer.startTag(null, "methodID");
                    serializer.endTag(null, "methodID");
                    serializer.endTag(null, "simpleUserDefined");
                }

                simpleUserDefinedEntries(serializer, plotCursor, community_observation, scope_plots);

                serializer.endTag(null, "communityObservation");
            }
        } finally {
            plotCursor.close();
        }
        if (started) {
            serializer.endTag(null, "communityObservations");
        }
    }

    //************************************************************//
    //***************** surface cover observations ***************//
    //************************************************************//

    private void writeSurfaceCoverObservations(XmlSerializer serializer) throws IOException {
        //cursors
        Cursor plotCursor = db.rawQuery("Select * FROM plot WHERE project_id =  \"" + MainActivity.exportedProjectId + "\"", null);
        String _id, value;
        boolean started = false;
        boolean surfaceTypesAvailable = false;
        boolean customSurfaceTypesAvailable = false;

        //plot observations loop
        try {
            while (plotCursor.moveToNext()) {



                for (int i = 0; i < surfaceTypes.length; i++) {
                    value = plotCursor.getString(plotCursor.getColumnIndex(surfaceTypes[i][1]));
                    if (value != null) {
                        surfaceTypesAvailable = true;
                    }
                }


                Cursor custom_fields_cursor = db.rawQuery("select * from custom_fields where type = \"" + surface_cover + "\" and scope = \"" + scope_plots + "\" and profile = \"" + profileId + "\"", null);
                if (custom_fields_cursor.moveToFirst()) {
                    try {
                        do {
                            customSurfaceTypesAvailable = true;
                        } while (custom_fields_cursor.moveToNext());
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        custom_fields_cursor.close();
                    }
                }

                if (custom_fields_cursor.getCount() > 0 ) {
                    Log.i("162", "writeSurfaceCoverObservations: Observations available!");
                }
                
                
                if (!started && (customSurfaceTypesAvailable || surfaceTypesAvailable ) ) {
                    serializer.startTag(null, "surfaceCoverObservations");
                    started = true;
                }
                _id = plotCursor.getString(plotCursor.getColumnIndex("_id"));
                for (int i = 0; i < surfaceTypes.length; i++) {
                    value = plotCursor.getString(plotCursor.getColumnIndex(surfaceTypes[i][1]));
                    if (value != null) {
                        serializer.startTag(null, "surfaceCoverObservation");
                        serializer.attribute(null, "id", "surfaceCoverObservation_" + _id + "_" + surfaceTypes[i][1]);
                        serializer.startTag(null, "plotObservationID");
                        serializer.text("plotObservation_" + _id);
                        serializer.endTag(null, "plotObservationID");

                        serializer.startTag(null, "surfaceTypeID");
                        serializer.text("surfaceType_" + surfaceTypes[i][1]);
                        serializer.endTag(null, "surfaceTypeID");

                        serializer.startTag(null, "surfaceCover");
                        serializer.startTag(null, "value");
                        serializer.text(value);
                        serializer.endTag(null, "value");
                        serializer.startTag(null, "attributeID");
                        serializer.text("percent_cover_of_surface_material");
                        serializer.endTag(null, "attributeID");
                        serializer.endTag(null, "surfaceCover");

                        serializer.endTag(null, "surfaceCoverObservation");
                    }
                }

                custom_fields_cursor = db.rawQuery("select * from custom_fields where type = \"" + surface_cover + "\" and scope = \"" + scope_plots + "\" and profile = \"" + profileId + "\"", null);
                if (custom_fields_cursor.moveToFirst()) {
                    try {
                        do {
                            String one = null;
                            String two = null;
                            String three = null;
                            String four = null;

                            try {
                                one = "surfaceCoverObservation_" + _id + "_" + custom_fields_cursor.getString(custom_fields_cursor.getColumnIndexOrThrow("name"));
                                two = "surfaceType_" + custom_fields_cursor.getString(custom_fields_cursor.getColumnIndexOrThrow("name"));
                                three = plotCursor.getString(plotCursor.getColumnIndexOrThrow(custom_fields_cursor.getString(custom_fields_cursor.getColumnIndexOrThrow("name")) + "_" + scope_plots));
                                four = custom_fields_cursor.getString(custom_fields_cursor.getColumnIndexOrThrow("attribute")) + "_" + scope_plots;
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            serializer.startTag(null, "surfaceCoverObservation");
                            serializer.attribute(null, "id", one);
                            serializer.startTag(null, "plotObservationID");
                            serializer.text("plotObservation_" + _id);
                            serializer.endTag(null, "plotObservationID");
                            serializer.startTag(null, "surfaceTypeID");
                            serializer.text(two);
                            serializer.endTag(null, "surfaceTypeID");
                            serializer.startTag(null, "surfaceCover");
                            serializer.startTag(null, "value");
                            if (three != null) {
                                serializer.text(three);
                            } else {
                                serializer.text("null");
                            }
                            serializer.endTag(null, "value");
                            serializer.startTag(null, "attributeID");
                            serializer.text(four);
                            serializer.endTag(null, "attributeID");
                            serializer.endTag(null, "surfaceCover");
                            serializer.endTag(null, "surfaceCoverObservation");
                        } while (custom_fields_cursor.moveToNext());
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        custom_fields_cursor.close();
                    }
                }
            }
        } finally {
            plotCursor.close();
        }
        if (started && (customSurfaceTypesAvailable || surfaceTypesAvailable ) ) {

            serializer.endTag(null, "surfaceCoverObservations");
        }
    }


    //***********************************************************//
    //***************** site Observations  **********************//
    //***********************************************************//

    // start plots
    private void writeSiteObversations(XmlSerializer serializer) throws IOException {
        //cursors
        Cursor plotCursor = db.rawQuery("Select * FROM plot WHERE project_id =  \"" + MainActivity.exportedProjectId + "\"", null);
        boolean started = false;

        //plot loop
        try {
            String _id, value;
            while (plotCursor.moveToNext()) {
                if (!started) {
                    serializer.startTag(null, "siteObservations");
                    started = true;
                }
                _id = plotCursor.getString(plotCursor.getColumnIndex("_id"));
                serializer.startTag(null, "siteObservation");
                serializer.attribute(null, "id", "siteObservation_" + _id);
                serializer.startTag(null, "plotObservationID");
                serializer.text("plotObservation_" + _id);
                serializer.endTag(null, "plotObservationID");

                serializer.startTag(null, "soilType");
                serializer.startTag(null, "value");
                value = plotCursor.getString(plotCursor.getColumnIndex("soil_type"));
                if (value != null) {
                    serializer.text(value);
                }
                serializer.endTag(null, "value");
                serializer.endTag(null, "soilType");

                serializer.startTag(null, "humusType");
                serializer.startTag(null, "value");
                value = plotCursor.getString(plotCursor.getColumnIndex("humus_type"));
                if (value != null) {
                    serializer.text(value);
                }
                serializer.endTag(null, "value");
                serializer.endTag(null, "humusType");

                serializer.startTag(null, "legalProtection");
                serializer.startTag(null, "value");
                value = plotCursor.getString(plotCursor.getColumnIndex("legal_status_1"));
                if (value != null) {
                    serializer.text(value);
                }
                serializer.endTag(null, "value");
                serializer.endTag(null, "legalProtection");

                serializer.startTag(null, "legalProtection");
                serializer.startTag(null, "value");
                value = plotCursor.getString(plotCursor.getColumnIndex("legal_status_2"));
                if (value != null) {
                    serializer.text(value);
                }
                serializer.endTag(null, "value");
                serializer.endTag(null, "legalProtection");

                serializer.startTag(null, "landuse");
                serializer.startTag(null, "value");
                value = plotCursor.getString(plotCursor.getColumnIndex("land_use"));
                if (value != null) {
                    serializer.text(value);
                }
                serializer.endTag(null, "value");
                serializer.endTag(null, "landuse");

                serializer.startTag(null, "habitat");
                serializer.startTag(null, "value");
                value = plotCursor.getString(plotCursor.getColumnIndex("habitat_type_1"));
                if (value != null) {
                    serializer.text(value);
                }
                serializer.endTag(null, "value");
                serializer.endTag(null, "habitat");

                serializer.startTag(null, "habitat");
                serializer.startTag(null, "value");
                value = plotCursor.getString(plotCursor.getColumnIndex("habitat_type_2"));
                if (value != null) {
                    serializer.text(value);
                }
                serializer.endTag(null, "value");
                serializer.endTag(null, "habitat");

                serializer.startTag(null, "simpleUserDefined");
                serializer.startTag(null, "name");
                serializer.text("Drainage");
                serializer.endTag(null, "name");
                serializer.startTag(null, "value");
                value = plotCursor.getString(plotCursor.getColumnIndex("watertable_depth"));
                if (value != null) {
                    serializer.text(value);
                }
                serializer.endTag(null, "value");
                serializer.startTag(null, "methodID");
                serializer.endTag(null, "methodID");
                serializer.endTag(null, "simpleUserDefined");

                serializer.startTag(null, "simpleUserDefined");
                serializer.startTag(null, "name");
                serializer.text("Management");
                serializer.endTag(null, "name");
                serializer.startTag(null, "value");
                value = plotCursor.getString(plotCursor.getColumnIndex("management"));
                if (value != null) {
                    serializer.text(value);
                }
                serializer.endTag(null, "value");
                serializer.startTag(null, "methodID");
                serializer.endTag(null, "methodID");
                serializer.endTag(null, "simpleUserDefined");

                serializer.startTag(null, "simpleUserDefined");
                serializer.startTag(null, "name");
                serializer.text("Fertilization");
                serializer.endTag(null, "name");
                serializer.startTag(null, "value");
                value = plotCursor.getString(plotCursor.getColumnIndex("fertilization"));
                if (value != null) {
                    serializer.text(value);
                }
                serializer.endTag(null, "value");
                serializer.startTag(null, "methodID");
                serializer.endTag(null, "methodID");
                serializer.endTag(null, "simpleUserDefined");

                serializer.startTag(null, "simpleUserDefined");
                serializer.startTag(null, "name");
                serializer.text("Soil texture");
                serializer.endTag(null, "name");
                serializer.startTag(null, "value");
                value = plotCursor.getString(plotCursor.getColumnIndex("soil_texture"));
                if (value != null) {
                    serializer.text(value);
                }
                serializer.endTag(null, "value");
                serializer.startTag(null, "methodID");
                serializer.endTag(null, "methodID");
                serializer.endTag(null, "simpleUserDefined");

                serializer.startTag(null, "simpleUserDefined");
                serializer.startTag(null, "name");
                serializer.text("Soil depth");
                serializer.endTag(null, "name");
                serializer.startTag(null, "value");
                value = plotCursor.getString(plotCursor.getColumnIndex("soil_depth"));
                if (value != null) {
                    serializer.text(value);
                }
                serializer.endTag(null, "value");
                serializer.startTag(null, "methodID");
                serializer.endTag(null, "methodID");
                serializer.endTag(null, "simpleUserDefined");

                simpleUserDefinedEntries(serializer, plotCursor, site_observation, scope_plots);
                simpleUserDefinedEntries(serializer, plotCursor, legal_status, scope_plots);

                serializer.endTag(null, "siteObservation");
            }
        } finally {
            plotCursor.close();
        }
        if (started) {
            serializer.endTag(null, "siteObservations");
        }
    }

    // formats a dd/mm/yyyy string to yyyy-mm-dd
    private String ddmmyyyToyyyymmdd(String date) {
        if (date == null || date.isEmpty()) {
            return "";
        }
        return date.substring(6) + "-"
                + date.substring(3, 5) + "-"
                + date.substring(0, 2);
    }

}
