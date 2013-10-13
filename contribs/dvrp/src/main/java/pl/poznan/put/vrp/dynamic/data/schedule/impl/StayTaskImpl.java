/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2013 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package pl.poznan.put.vrp.dynamic.data.schedule.impl;

import pl.poznan.put.vrp.dynamic.data.network.Vertex;
import pl.poznan.put.vrp.dynamic.data.schedule.StayTask;


public class StayTaskImpl
    extends AbstractTask
    implements StayTask
{
    private final Vertex atVertex;


    public StayTaskImpl(int beginTime, int endTime, Vertex atVertex)
    {
        super(beginTime, endTime);
        this.atVertex = atVertex;
    }


    @Override
    public Vertex getAtVertex()
    {
        return atVertex;
    }


    @Override
    public TaskType getType()
    {
        return TaskType.STAY;
    }


    @Override
    public String toString()
    {
        return "S(@" + atVertex.getId() + ")" + commonToString();
    }
}