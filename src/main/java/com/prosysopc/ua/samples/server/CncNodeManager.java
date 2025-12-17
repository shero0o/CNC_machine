package com.prosysopc.ua.samples.server;

import com.prosysopc.ua.StatusException;
import com.prosysopc.ua.ValueRanks;
import com.prosysopc.ua.nodes.UaMethod;
import com.prosysopc.ua.nodes.UaNode;
import com.prosysopc.ua.server.*;
import com.prosysopc.ua.server.nodes.*;
import com.prosysopc.ua.stack.builtintypes.*;
import com.prosysopc.ua.stack.core.Argument;
import com.prosysopc.ua.stack.core.Identifiers;
import com.prosysopc.ua.stack.builtintypes.StatusCode;
import com.prosysopc.ua.stack.builtintypes.Variant;

import java.util.*;

public class CncNodeManager extends NodeManagerUaNode {

    private UaObjectNode machineNode;
    private UaVariableNode statusNode;
    private UaVariableNode spindleTargetNode;
    private UaVariableNode spindleActualNode;
    private UaVariableNode feedTargetNode;
    private UaVariableNode feedActualNode;
    private UaVariableNode toolLifeNode;
    private UaVariableNode coolantTempNode;
    private UaVariableNode xNode, yNode, zNode;
    private UaVariableNode productionProgressNode;
    private UaVariableNode alarmNode;

    private UaVariableNode machineNameNode, serialNode;
    private UaVariableNode plantNode, lineNode;
    private UaVariableNode orderNode, articleNode, quantityNode;
    private UaVariableNode cuttingForceX, cuttingForceY, cuttingForceZ;
    private UaVariableNode surfaceTarget, surfaceActual;
    private UaVariableNode goodPartsNode, badPartsNode, totalPartsNode;
    private UaVariableNode targetCoolantFlowNode, actualCoolantFlowNode;
    private UaVariableNode targetCycleTimeNode, actualCycleTimeNode;
    private UaVariableNode machiningPhaseNode;

    private final Random random = new Random();
    private final LinkedList<Double> lastDeviations = new LinkedList<>();

    public CncNodeManager(UaServer server, String namespaceUri) {
        super(server, namespaceUri);
    }

    @Override
    protected void init() throws StatusException {
        super.init();
        createCncMachine();
        createMethodNodes();
    }

    private void createCncMachine() throws StatusException {
        int ns = getNamespaceIndex();

        machineNode = new UaObjectNode(this, new NodeId(ns, "CncMachine"),
                new QualifiedName(ns, "CncMachine"), LocalizedText.english("CNC Machining Center"));
        addNodeAndReference(getServer().getNodeManagerRoot().getObjectsFolder(), machineNode, Identifiers.Organizes);

        statusNode = createVar("MachineStatus", "Running");
        spindleTargetNode = createVar("TargetSpindleSpeed", 8500.0);
        spindleActualNode = createVar("ActualSpindleSpeed", 8487.0);
        feedTargetNode = createVar("TargetFeedRate", 1200.0);
        feedActualNode = createVar("ActualFeedRate", 1198.0);
        toolLifeNode = createVar("ToolLifeRemaining", 73.2);
        coolantTempNode = createVar("CoolantTemperature", 22.5);
        xNode = createVar("X", 125.847);
        yNode = createVar("Y", 89.234);
        zNode = createVar("Z", -45.678);
        surfaceTarget = createVar("TargetSurfaceFinish", 0.8);
        surfaceActual = createVar("ActualSurfaceFinish", 0.75);
        productionProgressNode = createVar("ProductionOrderProgress", 57.5);
        alarmNode = createVar("AlarmMessage", "OK");
        machineNameNode = createVar("MachineName", "CNC-01");
        serialNode = createVar("MachineSerialNumber", "VMC850-2023-003");
        plantNode = createVar("Plant", "Munich Precision Manufacturing");
        lineNode = createVar("ProductionLine", "5-Axis Machining Cell C");
        orderNode = createVar("ProductionOrder", "PO-2024-AERO-0876");
        articleNode = createVar("Article", "ART-TB-7075-T6");
        quantityNode = createVar("OrderQuantity", 120.0);
        cuttingForceX = createVar("CuttingForceX", 245.7);
        cuttingForceY = createVar("CuttingForceY", 189.3);
        cuttingForceZ = createVar("CuttingForceZ", 567.8);
        targetCoolantFlowNode = createVar("TargetCoolantFlow", 25.0);
        actualCoolantFlowNode = createVar("ActualCoolantFlow", 24.8);
        targetCycleTimeNode = createVar("TargetCycleTime", 75.0);
        actualCycleTimeNode = createVar("ActualCycleTime", 73.2);
        machiningPhaseNode = createVar("MachiningPhase", "Roughing");
        goodPartsNode = createVar("GoodParts", 2847.0);
        badPartsNode = createVar("BadParts", 23.0);
        totalPartsNode = createVar("TotalParts", 2870.0);

    }


    private void createMethodNodes() throws StatusException {
        final int ns = getNamespaceIndex();

        java.util.function.Function<String, UaMethodNode> mkMethod = name -> {
            UaMethodNode m = new UaMethodNode(this, new NodeId(ns, name),
                    new QualifiedName(ns, name), LocalizedText.english(name));
            m.setExecutable(true);
            m.setUserExecutable(true);
            try {
                addNodeAndReference(machineNode, m, Identifiers.HasComponent);
            } catch (StatusException e) {
                throw new RuntimeException(e);
            }
            return m;
        };

        UaMethodNode startMethod       = mkMethod.apply("StartMachine");
        UaMethodNode stopMethod        = mkMethod.apply("StopMachine");
        UaMethodNode maintenanceMethod = mkMethod.apply("EnterMaintenanceMode");
        UaMethodNode resetMethod       = mkMethod.apply("ResetCounters");
        UaMethodNode homeAxesMethod    = mkMethod.apply("HomeAxes");
        UaMethodNode toolChangeMethod  = mkMethod.apply("ToolChange");
        addInputArgumentsProperty(toolChangeMethod, new Argument[]{
                new Argument("ToolNumber", Identifiers.Int32, ValueRanks.Scalar, null,
                        LocalizedText.english("Tool number to switch to"))
        });

        UaMethodNode loadProgramMethod = mkMethod.apply("LoadCncProgram");
        addInputArgumentsProperty(loadProgramMethod, new Argument[]{
                new Argument("ProgramName", Identifiers.String, ValueRanks.Scalar, null,
                        LocalizedText.english("Program name"))
        });

        UaMethodNode loadOrderMethod   = mkMethod.apply("LoadProductionOrder");
        addInputArgumentsProperty(loadOrderMethod, new Argument[]{
                new Argument("Order",    Identifiers.String, ValueRanks.Scalar, null, LocalizedText.english("Order number")),
                new Argument("Article",  Identifiers.String, ValueRanks.Scalar, null, LocalizedText.english("Article")),
                new Argument("Quantity", Identifiers.Double, ValueRanks.Scalar, null, LocalizedText.english("Target quantity"))
        });

        MethodManagerUaNode methodManager = new MethodManagerUaNode(this);
        methodManager.addCallListener(new CallableListener() {
            @Override
            public boolean onCall(ServiceContext serviceContext,
                                  NodeId objectId,
                                  UaNode objectNode,
                                  NodeId methodId,
                                  UaMethod method,
                                  Variant[] inputArguments,
                                  StatusCode[] inputArgResults,
                                  DiagnosticInfo[] inputArgDiag,
                                  Variant[] outputArguments) throws StatusException {
                try {
                    if (methodId.equals(startMethod.getNodeId())) {
                        CncNodeManager.this.startMachine();
                    } else if (methodId.equals(stopMethod.getNodeId())) {
                        CncNodeManager.this.stopMachine();
                    } else if (methodId.equals(maintenanceMethod.getNodeId())) {
                        CncNodeManager.this.enterMaintenanceMode();
                    } else if (methodId.equals(resetMethod.getNodeId())) {
                        CncNodeManager.this.resetCounters();
                    } else if (methodId.equals(homeAxesMethod.getNodeId())) {
                        CncNodeManager.this.homeAxes();
                    } else if (methodId.equals(toolChangeMethod.getNodeId())) {
                        int toolNum = 1;
                        if (inputArguments != null && inputArguments.length > 0) {
                            Object v = inputArguments[0].getValue();
                            if (v instanceof Number) toolNum = ((Number) v).intValue();
                            else if (v instanceof String) toolNum = Integer.parseInt((String) v);
                        }
                        CncNodeManager.this.toolChange(toolNum);
                    } else if (methodId.equals(loadProgramMethod.getNodeId())) {
                        String prog = "DefaultProgram";
                        if (inputArguments != null && inputArguments.length > 0 &&
                                inputArguments[0].getValue() != null)
                            prog = String.valueOf(inputArguments[0].getValue());
                        CncNodeManager.this.loadCncProgram(prog);
                    } else if (methodId.equals(loadOrderMethod.getNodeId())) {
                        String order = "", article = "";
                        double qty = 0.0;
                        if (inputArguments != null) {
                            if (inputArguments.length > 0 && inputArguments[0].getValue() != null)
                                order = String.valueOf(inputArguments[0].getValue());
                            if (inputArguments.length > 1 && inputArguments[1].getValue() != null)
                                article = String.valueOf(inputArguments[1].getValue());
                            if (inputArguments.length > 2 && inputArguments[2].getValue() != null) {
                                Object v = inputArguments[2].getValue();
                                if (v instanceof Number) qty = ((Number) v).doubleValue();
                                else if (v instanceof String) qty = Double.parseDouble((String) v);
                            }
                        }
                        CncNodeManager.this.loadProductionOrder(order, article, qty);
                    } else {
                        return false;
                    }

                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }
        });



    }

    private void addInputArgumentsProperty(UaMethodNode methodNode, Argument[] args) throws StatusException {
        final int ns = getNamespaceIndex();

        PlainProperty inputProp = new PlainProperty(
                this,
                new NodeId(ns, methodNode.getBrowseName().getName() + "_InputArguments"),
                new QualifiedName(ns, "InputArguments"),
                LocalizedText.english("InputArguments"));

        inputProp.setDataTypeId(Identifiers.Argument);
        inputProp.setValueRank(ValueRanks.OneDimension);
        inputProp.addReference(Identifiers.HasTypeDefinition, Identifiers.PropertyType, false);
        inputProp.setValue(new Variant(args));

        addNodeAndReference(methodNode, inputProp, Identifiers.HasProperty);
    }





    private PlainVariable<?> createVar(String name, Object value) throws StatusException {
        NodeId id = new NodeId(getNamespaceIndex(), name);
        QualifiedName qn = new QualifiedName(getNamespaceIndex(), name);
        LocalizedText ln = LocalizedText.english(name);

        PlainVariable<?> node = new PlainVariable<>(this, id, qn, ln);

        node.setDataTypeId(getDataTypeId(value));
        node.addReference(Identifiers.HasTypeDefinition, Identifiers.BaseDataVariableType, false);
        node.setDescription(LocalizedText.english("Variable " + name));
        node.setValue(new Variant(value));
        addNodeAndReference(machineNode, node, Identifiers.HasComponent);
        return node;
    }


    private void addVars(UaVariableNode... vars) throws StatusException {
        for (UaVariableNode v : vars) {
            addNodeAndReference(machineNode, v, Identifiers.HasComponent);
        }
    }


    public void startMachine() {
        setStatus("Starting");
        new Timer().schedule(new TimerTask() {
            public void run() { setStatus("Running"); }
        }, 2000);
    }

    public void stopMachine() {
        setStatus("Stopping");
        new Timer().schedule(new TimerTask() {
            public void run() { setStatus("Stopped"); }
        }, 2000);
    }

    public void enterMaintenanceMode() {
        setStatus("Maintenance");
    }

    public void resetCounters() {
        try {
            goodPartsNode.setValue(new Variant(0.0));
            badPartsNode.setValue(new Variant(0.0));
            totalPartsNode.setValue(new Variant(0.0));
            productionProgressNode.setValue(new Variant(0.0));
            System.out.println("Counters reset.");
        } catch (StatusException e) {
            e.printStackTrace();
        }
    }

    public void toolChange(int toolNumber) throws StatusException {
        System.out.println("Tool change initiated to Tool" + toolNumber);
        toolLifeNode.setValue(new Variant(100.0));
    }

    public void loadCncProgram(String name) {
        System.out.println("CNC program '" + name + "' loaded successfully.");
    }

    public void homeAxes() {
        try {
            xNode.setValue(new Variant(0.0));
            yNode.setValue(new Variant(0.0));
            zNode.setValue(new Variant(0.0));
            setStatus("Homed");
        } catch (StatusException e) {
            e.printStackTrace();
        }
    }

    public void loadProductionOrder(String order, String article, double quantity) {
        try {
            orderNode.setValue(new Variant(order));
            articleNode.setValue(new Variant(article));
            quantityNode.setValue(new Variant(quantity));
            productionProgressNode.setValue(new Variant(0.0));
            setStatus("Order Loaded");
        } catch (StatusException e) {
            e.printStackTrace();
        }
    }

    public void simulateCycle() {
        try {
            double target = (Double) spindleTargetNode.getValue().getValue().getValue();
            double newActual = target * (1.0 + (random.nextDouble() - 0.5) * 0.04);
            spindleActualNode.setValue(new Variant(newActual));

            double feedT = (Double) feedTargetNode.getValue().getValue().getValue();
            double newFeed = feedT * (1.0 + (random.nextDouble() - 0.5) * 0.03);
            feedActualNode.setValue(new Variant(newFeed));

            double toolLife = (Double) toolLifeNode.getValue().getValue().getValue();
            toolLifeNode.setValue(new Variant(Math.max(toolLife - 0.2, 0)));
            double sfTarget = (Double) surfaceTarget.getValue().getValue().getValue();
            double sfActual = sfTarget + (random.nextDouble() - 0.5) * 0.02;
            surfaceActual.setValue(new Variant(sfActual));

            checkAlarms(target, newActual, toolLife, sfTarget, sfActual);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void checkAlarms(double target, double actual, double toolLife,
                             double surfTarget, double surfActual) {
        try {
            double deviation = Math.abs(actual - target) / target;
            lastDeviations.add(deviation);
            if (lastDeviations.size() > 3) lastDeviations.removeFirst();

            boolean threeBad = lastDeviations.size() == 3 &&
                    lastDeviations.stream().allMatch(d -> d > 0.05);

            double fx = (Double) cuttingForceX.getValue().getValue().getValue();
            double fy = (Double) cuttingForceY.getValue().getValue().getValue();
            double fz = (Double) cuttingForceZ.getValue().getValue().getValue();
            double totalForce = Math.sqrt(fx * fx + fy * fy + fz * fz);

            if (deviation > 0.15)
                alarm("SpindleSpeed >15% deviation (actual=" + actual + ")");
            else if (threeBad)
                alarm("SpindleSpeed >5% for 3 cycles");
            else if (toolLife < 10)
                alarm("Tool wear alarm: life <10%");
            else if (totalForce > 1.5 * 600)
                alarm("Tool breakage detected: cutting force " + totalForce + "N");
            else if (Math.abs(surfActual - surfTarget) > 0.01)
                alarm("Dimensional tolerance ±0.01mm exceeded (Δ=" + (surfActual - surfTarget) + ")");
            else
                alarmNode.setValue(new Variant("OK"));
        } catch (StatusException e) {
            e.printStackTrace();
        }
    }

    private void alarm(String msg) {
        System.out.println("Alarm: " + msg);
        setStatus("Error");

        try {
            alarmNode.setValue(new Variant(msg));
        } catch (StatusException e) {
            e.printStackTrace();
        }
    }


    private void setStatus(String s) {
        try {
            statusNode.setValue(new Variant(s));
        } catch (StatusException e) {
            e.printStackTrace();
        }
    }

    private NodeId getDataTypeId(Object value) {
        if (value instanceof String) return Identifiers.String;
        if (value instanceof Integer) return Identifiers.Int32;
        if (value instanceof Double || value instanceof Float) return Identifiers.Double;
        if (value instanceof Boolean) return Identifiers.Boolean;
        return Identifiers.BaseDataType;
    }


}
