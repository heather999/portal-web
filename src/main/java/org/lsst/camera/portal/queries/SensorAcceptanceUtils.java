package org.lsst.camera.portal.queries;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.lang.Math;
import java.util.Objects;
import javax.servlet.jsp.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpSession;
import org.srs.web.base.db.ConnectionManager;
import org.lsst.camera.portal.data.MetData;


/**
 *
 * @author heather 
 */
public class SensorAcceptanceUtils {

  
   
    public static Map<String, Map<String, List<Object>>> getSensorReportValues(HttpSession session, Integer actParentId, String reportName) throws SQLException, ServletException, JspException {

        Map<String, Map<String, List<Object>>> result = new LinkedHashMap<>(); // orders the elements in the same order they're processed instead of random order.

        //try (Connection c = ConnectionManager.getConnection("jdbc/config-prod")) {
        try (Connection c = ConnectionManager.getConnection("jdbc/config-dev")) {
            // FIXME: We should not hard-wire the DEV connection here.
            try (Connection oraconn = ConnectionManager.getConnection(session)) {

                int reportId = 1; // setting a default for now
                PreparedStatement reportIdStmt = c.prepareStatement("select id from report where name=?");
                reportIdStmt.setString(1, reportName);
                ResultSet reportIdResult = reportIdStmt.executeQuery();
                if (reportIdResult.next())  // for now just get the first instance, there are two rows with "vendorIngest" as the name
                    reportId = reportIdResult.getInt("id");
              
                PreparedStatement stmt = c.prepareStatement("select rkey, id, query from report_queries where report=?");
                stmt.setInt(1, reportId);
                ResultSet r = stmt.executeQuery();
                while (r.next()) {
                    String key = r.getString("rkey");
                    String tmpstr = r.getString("query");
                    Map<String, List<Object>> map = new HashMap<>();
                    PreparedStatement stmt2 = oraconn.prepareStatement(tmpstr);
                    stmt2.setInt(1, actParentId);
                    ResultSet q = stmt2.executeQuery();
                    int nCol = q.getMetaData().getColumnCount();
                    for (int col=1; col<=nCol; col++) {
                        map.put(q.getMetaData().getColumnName(col),new ArrayList<>());
                    }
                    while (q.next()) {
                        for (int col=1; col<=nCol; col++ ) {
                            String colName = q.getMetaData().getColumnName(col);
                            map.get(colName).add(q.getObject(col));
                        }
                    }
                    result.put(key, map);
                }
            }
        }
        return result;
    }
    
    public static List getMetReportValues(HttpSession session, String manu, Integer actParentId, Boolean haveMet05, Integer met05ParentId) throws SQLException {
        List<MetData> result = new ArrayList<>();
        
        Connection c = null;
        try {
         c = ConnectionManager.getConnection(session);
         
        Double BadValue = -999.0, Tolerance = 0.001;
         
         String ccd030 = "CCD-030";
         String ccd030a = "CCD-030a";
         String ccd030b = "CCD-030b";
         String ccd030c = "CCD-030c";
         String ccd031 = "CCD-031";
         
         String ccd030Desc = "Nominal height and Sensor height";
         String ccd030aDesc = "Nominal height (znom)";
         String ccd030bDesc = "Nominal height (zmedian)";
         String ccd030cDesc = "Sensor height";
         String ccd031Desc = "Sensor Surface Flatness";
         
         String ccd030Spec = "|znom-13 mm|<25 &micro && |zmedian-13 mm|<25 &micro && Z95halfband < 9 &micro";
         String ccd030aSpec = "|znom-13 mm|<25 &micro";
         String ccd030bSpec = "|zmedian-13 mm|<25 &micro";
         String ccd030cSpec = "Z95halfband < 9 &micro";
         String ccd031Spec = "flatnesshalfband_95 < 5 &micro";
         
         MetData ccd030Data = new MetData(ccd030, ccd030Desc, ccd030Spec);
         MetData ccd030aData = new MetData(ccd030a, ccd030aDesc, ccd030aSpec);
         MetData ccd030bData = new MetData(ccd030b, ccd030bDesc, ccd030bSpec);
         MetData ccd030cData = new MetData(ccd030c, ccd030cDesc, ccd030cSpec);
         MetData ccd031Data = new MetData(ccd031, ccd031Desc, ccd031Spec);

         PreparedStatement ccd030aStatement = c.prepareStatement("SELECT res.activityId, res.value AS znom FROM"
                 + " FloatResultHarnessed res JOIN Activity act ON res.activityId=act.id "
                 + " WHERE lower(res.schemaName) = 'metrology_vendoringest' AND res.name='znom' "
                 + " AND act.parentActivityId=?");
         ccd030aStatement.setInt(1, actParentId);
         ResultSet ccd030aResult = ccd030aStatement.executeQuery();

         
         PreparedStatement ccd030bStatement = c.prepareStatement("SELECT res.activityId, res.value AS zmedian"
                 + " FROM FloatResultHarnessed res join Activity act ON res.activityId=act.id "
                 + " WHERE lower(res.schemaName) = 'metrology_vendoringest' AND res.name='zmedian' "
                 + " AND act.parentActivityId=?");
         ccd030bStatement.setInt(1, actParentId);
         ResultSet ccd030bResult = ccd030bStatement.executeQuery();

                 
         PreparedStatement ccd030cStatement = c.prepareStatement("SELECT res.activityId, res.value AS z95halfband "
                 + " FROM FloatResultHarnessed res JOIN Activity act ON res.activityId=act.id "
                 + " WHERE lower(res.schemaName) = 'metrology_vendoringest' AND res.name='z95halfband' "
                 + " AND act.parentActivityId=?");
         ccd030cStatement.setInt(1, actParentId);
         ResultSet ccd030cResult = ccd030cStatement.executeQuery();
         
         if (ccd030aResult.first() && ccd030bResult.first() && ccd030cResult.first()) {
             Double znom = ccd030aResult.getDouble("znom");
             Double zmedian = ccd030bResult.getDouble("zmedian");
             Double z95halfband = ccd030cResult.getDouble("z95halfband");
             Boolean znomGood = false;
             Boolean zmedianGood = false;
             Boolean z95halfbandGood = false;
             if (Math.abs(znom - BadValue) < Tolerance) {
                 ccd030aData.setVendorVendor("NA", false);
             } else {
                 znomGood = (Math.abs(znom - 13.)*1000 < 25); // converting the length in mm to microns *1000
                 ccd030aData.setVendorVendor(String.format("%.3f \u00B5", Math.abs(znom - 13.)*1000), znomGood);
             }
             if (Math.abs(zmedian - BadValue) < Tolerance) {
                 ccd030bData.setVendorVendor("NA", false);
             } else {
                 zmedianGood = (Math.abs(zmedian - 13.)*1000 < 25); // converting length in mm to mircons *1000
                 ccd030bData.setVendorVendor(String.format("%.3f \u00B5", Math.abs(zmedian - 13.)*1000), zmedianGood);
             }
             if (Math.abs(z95halfband - BadValue) < Tolerance) {
                 ccd030cData.setVendorVendor("NA", false);
             } else {
                 z95halfbandGood = (z95halfband < 0.009); // assuming z95halfband is in mm
                 ccd030cData.setVendorVendor(String.format("%.3f \u00B5", z95halfband*1000), z95halfbandGood); // reporting value in microns
             }

             Boolean ccd030Status = (znomGood && zmedianGood && z95halfbandGood);
             ccd030Data.setVendorVendor("...", ccd030Status);
             
             // Always leaving LSST-LSST Met Data set to NA for now
             
         }
         
         PreparedStatement ccd031Statement = c.prepareStatement("SELECT res.activityId, res.value AS flatness "
                 + " FROM FloatResultHarnessed res JOIN Activity act ON res.activityId=act.id "
                 + " WHERE lower(res.schemaName) = 'metrology_vendoringest' AND res.name='flatnesshalfband_95' "
                 + " AND act.parentActivityId=?");
         ccd031Statement.setInt(1, actParentId);
         ResultSet ccd031Result = ccd031Statement.executeQuery();

            if (ccd031Result.first()) {
                double flatness = ccd031Result.getDouble("flatness");
                if (Math.abs(flatness - BadValue) < Tolerance) {
                    ccd031Data.setVendorVendor("NA", false);
                } else {
                    ccd031Data.setVendorVendor(String.format("%.4f \u00B5", flatness), (flatness < 5.));
                }
            }

         
            if (haveMet05) {  // Continue with Vendor-LSST 
                String vlccd030Spec = "|z_median_m_13|<25 &micro && |z_quantile_0975 - z_quantile_0025|<18 &micro";
                String vlccd030aSpec = "...";
                String vlccd030bSpec = "|z_median_m_13|<25 &micro";
                String vlccd030cSpec = "|z_quantile_0975 - z_quantile_0025|<18 &micro";
                String vlccd031Spec = "peak_valley_95 < 10 &micro";

                PreparedStatement vlccd030bStatement = c.prepareStatement("SELECT res.activityId, res.value AS zmedian"
                        + " FROM FloatResultHarnessed res join Activity act ON res.activityId=act.id "
                        + " WHERE lower(res.schemaName) = 'sensor_abs_height' AND res.name='z_median_m_13' "
                        + " AND act.parentActivityId=?");
                vlccd030bStatement.setInt(1, met05ParentId);
                ResultSet vlccd030bResult = vlccd030bStatement.executeQuery();

                PreparedStatement vlccd030cStatement1 = c.prepareStatement("SELECT res.activityId, res.value AS z_quantile_0025 "
                        + " FROM FloatResultHarnessed res JOIN Activity act ON res.activityId=act.id "
                        + " WHERE lower(res.schemaName) = 'sensor_abs_height' AND res.name='z_quantile_0025' "
                        + " AND act.parentActivityId=?");
                vlccd030cStatement1.setInt(1, met05ParentId);
                ResultSet vlccd030cResult1 = vlccd030cStatement1.executeQuery();

                PreparedStatement vlccd030cStatement2 = c.prepareStatement("SELECT res.activityId, res.value AS z_quantile_0975 "
                        + " FROM FloatResultHarnessed res JOIN Activity act ON res.activityId=act.id "
                        + " WHERE lower(res.schemaName) = 'sensor_abs_height' AND res.name='z_quantile_0975' "
                        + " AND act.parentActivityId=?");
                vlccd030cStatement2.setInt(1, met05ParentId);
                ResultSet vlccd030cResult2 = vlccd030cStatement2.executeQuery();

                PreparedStatement vlccd031Statement = c.prepareStatement("SELECT res.activityId, res.value AS peak_valley_95 "
                        + " FROM FloatResultHarnessed res JOIN Activity act ON res.activityId=act.id "
                        + " WHERE lower(res.schemaName) = 'sensor_flatness' AND res.name='peak_valley_95' "
                        + " AND act.parentActivityId=?");
                vlccd031Statement.setInt(1, met05ParentId);
                ResultSet vlccd031Result = vlccd031Statement.executeQuery();

                if (vlccd030bResult.first() && vlccd030cResult1.first() && vlccd030cResult2.first()) {
                    Double z_median_m_13 = vlccd030bResult.getDouble("zmedian");
                    Double z_quantile_0025 = vlccd030cResult1.getDouble("z_quantile_0025");
                    Double z_quantile_0975 = vlccd030cResult2.getDouble("z_quantile_0975");
                    ccd030bData.setVendorLsst(String.format("%.3f \u00B5", Math.abs(z_median_m_13)), (Math.abs(z_median_m_13) < 25));
                    ccd030cData.setVendorLsst(String.format("%.3f \u00B5", Math.abs(z_quantile_0975 - z_quantile_0025)), (Math.abs(z_quantile_0975 - z_quantile_0025) < 18.));
                    Boolean vlccd030Status = (Math.abs(z_median_m_13) < 25) && (Math.abs(z_quantile_0975 - z_quantile_0025) < 18.);
                    ccd030Data.setVendorLsst("...", vlccd030Status);
                    
                    ccd030Data.setVendlsstSpecification(vlccd030Spec);
                    ccd030bData.setVendlsstSpecification(vlccd030bSpec);
                    ccd030cData.setVendlsstSpecification(vlccd030cSpec);
                 
                }
            
                if (vlccd031Result.first()) {
                    Double peak_valley_95 = vlccd031Result.getDouble("peak_valley_95");
                    ccd031Data.setVendorLsst(String.format("%.3f",peak_valley_95), peak_valley_95 < 10.); // See LSSTTD-812
                    
                    ccd031Data.setVendlsstSpecification(vlccd031Spec);
                }
            }
            
         
            if (Objects.equals(manu.toUpperCase(), "ITL")) {
                result.add(ccd030Data);
                result.add(ccd030aData);
                result.add(ccd030bData);
                result.add(ccd030cData);
            }
            result.add(ccd031Data);  // We only have Vendor-LSST CCD-031 for e2v 

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (c != null) {
                //Close the connection
                c.close();
            }
        }

        return result;
        }
    }
    

