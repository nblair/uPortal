/**
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.jasig.portal.tools.versioning;

/**
 * Represents the version of a specific unit of code that is currently installed
 * in the portal. The unit of code is represented by its functional name. For
 * the portal codebase this functional name uses the value of the IPermission
 * interface, PORTAL_FRAMEWORK variable which currently is "UP_FRAMEWORK".
 *
 * The version of a specific piece of code is represented by three integers.
 * In most significant order these are Major, Minor, and Micro.
 *
 * @author Mark Boyd  {@link <a href="mailto:mark.boyd@engineer.com">mark.boyd@engineer.com</a>}
 * @version $Revision$
 */
public class Version
{
    private String fname = null;
    private String description = null;
    private int major = 0;
    private int minor = 0;
    private int micro = 0;

    /**
     * Only the versioning package classes are expected to create these
     * objects.
     *
     * @param fname the functional name of the code being represented
     * @param major the major version of this code
     * @param minor the minor version of this code
     * @param micro the micro version of this code
     */
    public Version(String fname, String description, int major, int minor, int micro)
    {
        this.fname = fname;
        this.description = description;
        this.major = major;
        this.minor = minor;
        this.micro = micro;
    }

    /**
     * Returns the functional name of the unit of code for which this object
     * represents the version of this code that is currently installed in the
     * portal.
     *
     * @return String
     */
    public String getFname()
    {
        return fname;
    }

    /**
     * Returns the description of the unit of code for which this object
     * represents the version of this code that is currently installed in the
     * portal.
     *
     * @return String
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * The major version of the code represented by this object.
     *
     * @return int
     */
    public int getMajor()
    {
        return major;
    }

    /**
     * The minor version of the code represented by this object.
     *
     * @return int
     */
    public int getMinor()
    {
        return minor;
    }

    /**
     * The micro version of the code represented by this object.
     *
     * @return int
     */
    public int getMicro()
    {
        return micro;
    }

    /**
     * Returns a string representation of the version. The format is:
     * <functionalName>: <major>.<minor>.<micro>
     *
     * @return java.lang.String
     *
     */
    public String toString()
    {
        return this.getFname()
            + ": "
            + this.getMajor()
            + "."
            + this.getMinor()
            + "."
            + this.getMicro()
            + " ["
            + this.getDescription()
            + "]";
    }
    /**
     * Returns true of the passed in object is a Version object and the
     * functional names of the objects are equal and the version numbers
     * within this class are equal to those of the passed in version object.
     *
     * @param obj a Version object to be compared to this one
     * @return boolean true if the above conditions are met
     */
    public boolean equals(Object obj)
    {
        if (!(obj instanceof Version))
            return false;

        Version v = (Version) obj;

        return fname.equals(v.getFname()) && equalTo(v);
    }

    /**
     * Returns true of the version represented by this class is equal to
     * that represented by the passed in version object. The functional name
     * is not queried for this evaluation. Only version numbers are compared.
     *
     * @param v the version to be compared with this version
     * @return boolean true if this version is equal to the passed in version
     */
    public boolean equalTo(Version v)
    {
        return major == v.getMajor()
            && minor == v.getMinor()
            && micro == v.getMicro();
    }

    /**
     * Returns true of the version represented by this class is less than
     * that represented by the passed in version object. The functional name
     * is not queried for this evaluation. Only version numbers are compared.
     *
     * @param v the version to be compared with this version
     * @return boolean true if this version is less than to the passed in version
     */
    public boolean lessThan(Version v)
    {
        return major < v.getMajor()
            || ( major == v.getMajor() && ( minor < v.getMinor()))
            || (major == v.getMajor()
                && minor == v.getMinor()
                && micro < v.getMicro());
    }

    /**
     * Returns true of the version represented by this class is greater than
     * that represented by the passed in version object. The functional name
     * is not queried for this evaluation. Only version numbers are compared.
     *
     * @param v the version to be compared with this version
     * @return boolean true if this version is greater than to the passed in version
     */
    public boolean greaterThan(Version v)
    {
        return major > v.getMajor()
            || ( major == v.getMajor() && ( minor > v.getMinor()))
            || (major == v.getMajor()
                && minor == v.getMinor()
                && micro > v.getMicro());
    }

    /**
     * Returns a String of the form "2.5.4".
     * @return a String representing the dotted triple version
     */
    public String dottedTriple() {
        return this.major + "." + this.minor + "." + this.micro;
    }
}