-- 高等数学1（课程空间 910000000002）演示章节与学习内容。
-- 追加第一章~第四章（微积分），每节附完整学习内容与知识点。
SET NAMES utf8mb4;

INSERT INTO edu_course_unit(course_space_id, parent_unit_id, unit_kind, title, sort_order, content_text)
VALUES (910000000002, NULL, 'CHAPTER', '第一章 函数与极限', 1, NULL);
SET @ch1 = LAST_INSERT_ID();

INSERT INTO edu_course_unit(course_space_id, parent_unit_id, unit_kind, title, sort_order, content_text)
VALUES (910000000002, @ch1, 'SECTION', '1.1 函数的概念与性质', 0, '函数是描述变量之间依赖关系的数学工具。设 D 是一个非空实数集合，若对 D 中每个数 x，按照确定的法则 f 都有唯一实数 y 与之对应，则称 f 为定义在 D 上的函数，记作 y=f(x)，x 称为自变量，y 称为因变量，D 称为定义域，函数值的全体称为值域。

函数的表示方法主要有三种：解析法、表格法和图象法。解析法便于理论研究，图象法直观反映函数的整体性态。

常用的函数性质包括：有界性——若存在正数 M 使得对定义域内任意 x 都有 |f(x)|≤M，则称 f 有界；单调性——在区间 I 上任意 x1<x2 都有 f(x1)≤f(x2)（或相反），则称 f 单调递增（或递减）；奇偶性——f(-x)=-f(x) 时为奇函数，f(-x)=f(x) 时为偶函数；周期性——存在非零常数 T 使得 f(x+T)=f(x)。

基本初等函数包括：幂函数、指数函数、对数函数、三角函数与反三角函数。由常数和基本初等函数经过有限次四则运算和复合运算得到的函数称为初等函数，微积分研究的主要对象就是初等函数。');
SET @s1 = LAST_INSERT_ID();

INSERT INTO edu_course_unit(course_space_id, parent_unit_id, unit_kind, title, sort_order, content_text)
VALUES (910000000002, @ch1, 'SECTION', '1.2 数列的极限', 1, '数列是按正整数编号的一列数 x1, x2, ..., xn, ...，记作 {xn}。极限研究的是当 n 无限增大时数列的变化趋势。

定义：若存在常数 a，使得对任意给定的正数 ε（无论多么小），总存在正整数 N，当 n>N 时恒有 |xn-a|<ε，则称 a 为数列 {xn} 的极限，记作 lim(n→∞) xn = a，此时称数列收敛；否则称数列发散。这个定义称为极限的 ε-N 定义，它用有限的语言刻画了无限的过程。

收敛数列具有以下重要性质：极限的唯一性——收敛数列的极限必唯一；有界性——收敛数列必有界；保号性——若极限为正，则从某项起数列各项保持同号；子列性质——收敛数列的任意子列收敛于同一极限。

典型极限如 lim(n→∞) q^n = 0（|q|<1），lim(n→∞) (1+1/n)^n = e，是后续学习的基石。');
SET @s2 = LAST_INSERT_ID();

INSERT INTO edu_course_unit(course_space_id, parent_unit_id, unit_kind, title, sort_order, content_text)
VALUES (910000000002, @ch1, 'SECTION', '1.3 函数的极限与运算法则', 2, '函数极限描述自变量趋于某值时函数值的变化趋势，包括 x→x0 的极限与 x→∞ 的极限两类。

定义（x→x0）：若存在常数 A，对任意 ε>0，存在 δ>0，当 0<|x-x0|<δ 时恒有 |f(x)-A|<ε，则称 A 为 f(x) 当 x→x0 时的极限。注意极限过程与函数在 x0 处是否有定义无关。

极限的四则运算法则：若 lim f(x)=A，lim g(x)=B，则 lim[f(x)±g(x)]=A±B，lim[f(x)·g(x)]=A·B，当 B≠0 时 lim[f(x)/g(x)]=A/B。这些法则把复杂函数的极限化归为简单函数的极限。

两个重要极限：lim(x→0) sinx/x = 1，lim(x→∞) (1+1/x)^x = e。它们分别刻画了三角函数与指数函数的局部性态，是求极限的重要工具。

夹逼准则：若在 x0 的某去心邻域内 g(x)≤f(x)≤h(x)，且 lim g(x)=lim h(x)=A，则 lim f(x)=A。单调有界准则：单调有界数列必有极限。');
SET @s3 = LAST_INSERT_ID();

INSERT INTO edu_course_unit(course_space_id, parent_unit_id, unit_kind, title, sort_order, content_text)
VALUES (910000000002, @ch1, 'SECTION', '1.4 无穷小与无穷大', 3, '以零为极限的函数（或数列）称为无穷小。无穷小是一个极限过程概念，除零以外任何很小的常数都不是无穷小。若 lim[f(x)/g(x)]=0，则称 f 是比 g 高阶的无穷小；若极限为非零常数，则称二者同阶；若极限为 1，则称二者等价。

等价无穷小替换是求极限的常用技巧：当 x→0 时，sinx~x，tanx~x，arcsinx~x，arctanx~x，ln(1+x)~x，e^x-1~x，1-cosx~x²/2。在乘除运算中可以整体替换，但加减运算中一般不可随意替换。

绝对值无限增大的变量称为无穷大。无穷大与无穷小互为倒数关系：在自变量同一变化过程中，若 f(x) 为无穷小且 f(x)≠0，则 1/f(x) 为无穷大。

理解无穷小与无穷大的关系，是掌握极限理论、理解微分概念（dy 与 Δy 的关系）的关键。');
SET @s4 = LAST_INSERT_ID();

INSERT INTO edu_course_unit(course_space_id, parent_unit_id, unit_kind, title, sort_order, content_text)
VALUES (910000000002, NULL, 'CHAPTER', '第二章 导数与微分', 2, NULL);
SET @ch2 = LAST_INSERT_ID();

INSERT INTO edu_course_unit(course_space_id, parent_unit_id, unit_kind, title, sort_order, content_text)
VALUES (910000000002, @ch2, 'SECTION', '2.1 导数的概念', 0, '导数源于两个经典问题：变速直线运动的瞬时速度与曲线切线的斜率，二者的数学结构完全一致——增量比的极限。

定义：设函数 y=f(x) 在点 x0 的某邻域内有定义，若极限 lim(Δx→0) [f(x0+Δx)-f(x0)]/Δx 存在，则称 f 在 x0 处可导，此极限值称为 f 在 x0 处的导数，记作 f\'(x0)。导数 f\'(x0) 的几何意义是曲线 y=f(x) 在点 (x0, f(x0)) 处切线的斜率。

可导与连续的关系：可导必连续，连续不一定可导。经典反例是 y=|x| 在 x=0 处连续但不可导。

常见函数的导数公式：(x^n)\'=nx^(n-1)，(sinx)\'=cosx，(cosx)\'=-sinx，(e^x)\'=e^x，(lnx)\'=1/x。这些基本公式是后续求导运算的基础。');
SET @s5 = LAST_INSERT_ID();

INSERT INTO edu_course_unit(course_space_id, parent_unit_id, unit_kind, title, sort_order, content_text)
VALUES (910000000002, @ch2, 'SECTION', '2.2 求导法则', 1, '求导的四则运算法则：若 u、v 可导，则 (u±v)\'=u\'±v\'，(uv)\'=u\'v+uv\'，(u/v)\'=(u\'v-uv\')/v²（v≠0）。

复合函数求导的链式法则：若 y=f(u)，u=g(x) 均可导，则 dy/dx = dy/du · du/dx。链式法则是求导的核心技术，可推广到多层复合，熟练后应做到"由外向内逐层求导"。

隐函数求导：对由方程 F(x,y)=0 确定的隐函数，对方程两边关于 x 求导（把 y 视为 x 的函数），再解出 y\'。常用于求圆、椭圆等曲线在某点的切线。

参数方程求导：若 x=φ(t)，y=ψ(t)，则 dy/dx = ψ\'(t)/φ\'(t)。');
SET @s6 = LAST_INSERT_ID();

INSERT INTO edu_course_unit(course_space_id, parent_unit_id, unit_kind, title, sort_order, content_text)
VALUES (910000000002, @ch2, 'SECTION', '2.3 高阶导数与微分', 2, '导数的导数称为二阶导数，记作 f\'\'(x) 或 d²y/dx²。二阶导数描述函数图象的凹凸性，物理上对应加速度。一般地，n 阶导数是对 (n-1) 阶导数再求导。

微分的概念：设函数 y=f(x) 在点 x0 可导，则增量 Δy 可表示为 Δy=f\'(x0)Δx+o(Δx)，其中 f\'(x0)Δx 称为函数在 x0 处的微分，记作 dy。dy 是 Δy 的线性主部，当 Δx 很小时 Δy≈dy。

微分与导数的关系：dy=f\'(x)dx，即微分等于导数乘以自变量的微分。微分形式 dy=f\'(x)dx 在换元后形式不变，这一性质称为微分形式不变性。

微分的应用：近似计算 f(x0+Δx)≈f(x0)+f\'(x0)Δx，误差估计。例如求 sin31° 的近似值，可借助 sinx 在 x=30° 处的微分完成。');
SET @s7 = LAST_INSERT_ID();

INSERT INTO edu_course_unit(course_space_id, parent_unit_id, unit_kind, title, sort_order, content_text)
VALUES (910000000002, NULL, 'CHAPTER', '第三章 微分中值定理与导数的应用', 3, NULL);
SET @ch3 = LAST_INSERT_ID();

INSERT INTO edu_course_unit(course_space_id, parent_unit_id, unit_kind, title, sort_order, content_text)
VALUES (910000000002, @ch3, 'SECTION', '3.1 微分中值定理', 0, '罗尔定理：若 f(x) 在 [a,b] 上连续，在 (a,b) 内可导，且 f(a)=f(b)，则存在 ξ∈(a,b) 使得 f\'(ξ)=0。几何上说明水平弦的两端之间必有水平切线。

拉格朗日中值定理：若 f(x) 在 [a,b] 上连续，在 (a,b) 内可导，则存在 ξ∈(a,b) 使得 f\'(ξ)=[f(b)-f(a)]/(b-a)。它把函数的平均变化率与某点的瞬时变化率联系起来，是导数应用的理论基础。

柯西中值定理：若 f、g 在 [a,b] 连续，(a,b) 内可导且 g\'(x)≠0，则存在 ξ 使得 [f(b)-f(a)]/[g(b)-g(a)] = f\'(ξ)/g\'(ξ)。柯西定理是证明洛必达法则的关键。

三个定理层层递进：罗尔定理是拉格朗日定理的特例，拉格朗日定理是柯西定理的特例。');
SET @s8 = LAST_INSERT_ID();

INSERT INTO edu_course_unit(course_space_id, parent_unit_id, unit_kind, title, sort_order, content_text)
VALUES (910000000002, @ch3, 'SECTION', '3.2 洛必达法则', 1, '0/0 型与 ∞/∞ 型未定式可以通过洛必达法则求极限：若 lim f(x)=lim g(x)=0（或同为无穷大），且 f、g 可导、g\'≠0，若 lim[f\'(x)/g\'(x)] 存在（或为无穷大），则 lim[f(x)/g(x)] = lim[f\'(x)/g\'(x)]。

使用洛必达法则的注意事项：必须先确认是 0/0 或 ∞/∞ 型；每次使用前都要检查条件；若求导后仍未定，可继续使用；若 lim[f\'/g\'] 不存在且不是无穷大，不能说明原极限不存在，应改用其他方法。

0·∞、∞-∞、0^0、1^∞、∞^0 等未定式需先通过代数变形转化为 0/0 或 ∞/∞ 型再使用法则。典型如 lim(x→0+) x·lnx 可改写为 lnx/(1/x)。

洛必达法则常与等价无穷小替换、重要极限配合使用，是求极限的综合技能。');
SET @s9 = LAST_INSERT_ID();

INSERT INTO edu_course_unit(course_space_id, parent_unit_id, unit_kind, title, sort_order, content_text)
VALUES (910000000002, @ch3, 'SECTION', '3.3 函数的单调性与极值', 2, '单调性判定：若在区间 I 上 f\'(x)>0，则 f 在 I 上单调递增；若 f\'(x)<0 则单调递减。驻点（f\'(x)=0 的点）与不可导点是划分单调区间的分界点。

极值的必要条件：若 f 在 x0 处取得极值且可导，则 f\'(x0)=0。第一充分条件：导数在 x0 两侧由正变负则取极大值，由负变正则取极小值；第二充分条件：f\'(x0)=0 且 f\'\'(x0)>0 取极小值，f\'\'(x0)<0 取极大值。

最值问题：连续函数在闭区间上的最值只需比较驻点、不可导点与端点的函数值。实际应用题（如用料最省、容积最大）先建立目标函数，再按此方法求最值。

凹凸性由二阶导数判定：f\'\'>0 为凹（向上弯），f\'\'<0 为凸；f\'\'=0 且两侧变号的点称为拐点。');
SET @s10 = LAST_INSERT_ID();

INSERT INTO edu_course_unit(course_space_id, parent_unit_id, unit_kind, title, sort_order, content_text)
VALUES (910000000002, NULL, 'CHAPTER', '第四章 不定积分与定积分', 4, NULL);
SET @ch4 = LAST_INSERT_ID();

INSERT INTO edu_course_unit(course_space_id, parent_unit_id, unit_kind, title, sort_order, content_text)
VALUES (910000000002, @ch4, 'SECTION', '4.1 原函数与不定积分', 0, '若在区间 I 上 F\'(x)=f(x)，则称 F 为 f 在 I 上的原函数。原函数族 F(x)+C（C 为任意常数）称为 f 的不定积分，记作 ∫f(x)dx。求导与求不定积分互为逆运算。

基本积分公式表由导数公式反推得到：∫x^n dx = x^(n+1)/(n+1)+C（n≠-1），∫(1/x)dx = ln|x|+C，∫e^x dx = e^x+C，∫sinx dx = -cosx+C，∫cosx dx = sinx+C。

不定积分的线性性质：∫[k1·f(x)+k2·g(x)]dx = k1∫f(x)dx + k2∫g(x)dx。这一性质使复杂函数可拆分为基本积分求解。

积分结果可用求导验算：对结果求导应还原为被积函数，这是检查答案的常规手段。');
SET @s11 = LAST_INSERT_ID();

INSERT INTO edu_course_unit(course_space_id, parent_unit_id, unit_kind, title, sort_order, content_text)
VALUES (910000000002, @ch4, 'SECTION', '4.2 换元积分法与分部积分法', 1, '第一类换元法（凑微分法）：若 ∫f(u)du = F(u)+C，则 ∫f[g(x)]g\'(x)dx = F[g(x)]+C。要点是把被积表达式凑成 g\'(x)dx = dg(x) 的形式，如 ∫sin2x dx 先凑成 (1/2)∫sin2x d(2x)。

第二类换元法：令 x=φ(t)，则 ∫f(x)dx = ∫f[φ(t)]φ\'(t)dt。常见的有三角代换（√(a²-x²) 令 x=a·sint）与根式代换（令 √x = t 消去根号）。

分部积分法：∫u dv = uv - ∫v du。适用于两类不同函数相乘的积分，如 ∫x·e^x dx（幂函数×指数函数）、∫x·lnx dx、∫e^x·sinx dx（指数函数×三角函数，需两次分部后回代求解）。

选择 u 的口诀是"反对幂指三"——反三角函数、对数函数、幂函数、指数函数、三角函数依次优先选作 u。');
SET @s12 = LAST_INSERT_ID();

INSERT INTO edu_course_unit(course_space_id, parent_unit_id, unit_kind, title, sort_order, content_text)
VALUES (910000000002, @ch4, 'SECTION', '4.3 定积分的概念与微积分基本定理', 2, '定积分源于曲边梯形的面积问题：把 [a,b] 分割成 n 个小区间，用矩形面积近似代替，取分割无限加细时的极限。若极限 lim Σf(ξi)Δxi 存在且与分割方式和取点无关，则称其为 f 在 [a,b] 上的定积分，记作 ∫(a→b) f(x)dx。

定积分的几何意义：f≥0 时表示曲边梯形面积；f 变号时为 x 轴上方面积减去下方面积。定积分的性质包括线性性、区间可加性、保号性与估值定理。

变上限积分函数 Φ(x)=∫(a→x) f(t)dt 是连接微分与积分的桥梁，Φ\'(x)=f(x)。

微积分基本定理（牛顿-莱布尼茨公式）：若 F 是 f 在 [a,b] 上的原函数，则 ∫(a→b) f(x)dx = F(b)-F(a)。它把定积分的计算归结为求原函数，是微积分最重要的定理。');
SET @s13 = LAST_INSERT_ID();

INSERT INTO edu_course_unit(course_space_id, parent_unit_id, unit_kind, title, sort_order, content_text)
VALUES (910000000002, @ch4, 'SECTION', '4.4 定积分的应用', 3, '定积分在几何与物理中有广泛应用。平面图形面积：由曲线 y=f(x)（f≥0）、直线 x=a、x=b 与 x 轴围成的曲边梯形面积为 A=∫(a→b) f(x)dx；由两条曲线 y=f(x)、y=g(x) 围成的面积为 A=∫(a→b) |f(x)-g(x)|dx。

旋转体体积：平面图形绕 x 轴旋转一周所得立体的体积为 V=π∫(a→b) f²(x)dx（圆盘法）。曲线弧长：光滑曲线 y=f(x) 在 [a,b] 上的弧长为 s=∫(a→b) √(1+[f\'(x)]²)dx。

物理应用：变力沿直线做功 W=∫(a→b) F(x)dx；液体侧压力等也可化为定积分计算；变速直线运动的路程 s=∫(t1→t2) v(t)dt，与速度、加速度互为积分与微分的关系。

解题步骤：选取积分变量与积分区间，确定微元表达式 dA（或 dV、dW），再积分为定积分并计算。画图、取微元、定限是三步关键。');
SET @s14 = LAST_INSERT_ID();

INSERT INTO edu_knowledge_point(course_space_id, unit_id, code, name, content_text) VALUES
(910000000002, @s1, 'KP-001', '函数', '函数的三要素：定义域、对应法则、值域；基本初等函数与初等函数的概念。'),
(910000000002, @s2, 'KP-002', '数列极限', '数列收敛的 ε-N 定义；收敛数列的唯一性、有界性与保号性。'),
(910000000002, @s3, 'KP-003', '函数极限', '函数极限的 ε-δ 定义；极限四则运算法则；两个重要极限与夹逼准则。'),
(910000000002, @s4, 'KP-004', '无穷小与无穷大', '无穷小的比较（高阶、同阶、等价）；等价无穷小替换；无穷大与无穷小的倒数关系。'),
(910000000002, @s5, 'KP-005', '导数', '导数的定义与几何意义；可导与连续的关系；基本求导公式。'),
(910000000002, @s6, 'KP-006', '求导法则', '四则运算求导法则；链式法则；隐函数与参数方程求导。'),
(910000000002, @s7, 'KP-007', '微分', '微分的定义；微分与导数的关系；微分形式不变性；近似计算。'),
(910000000002, @s8, 'KP-008', '微分中值定理', '罗尔定理、拉格朗日中值定理、柯西中值定理的条件与结论。'),
(910000000002, @s9, 'KP-009', '洛必达法则', '0/0 与 ∞/∞ 型未定式；其他未定式的转化；使用条件与误区。'),
(910000000002, @s10, 'KP-010', '单调性与极值', '单调性判定；极值的第一、第二充分条件；最值问题；凹凸性与拐点。'),
(910000000002, @s11, 'KP-011', '不定积分', '原函数与不定积分；基本积分表；不定积分的线性性质。'),
(910000000002, @s12, 'KP-012', '积分方法', '第一、第二类换元积分法；分部积分法及 u 的选取原则。'),
(910000000002, @s13, 'KP-013', '定积分', '定积分的定义与几何意义；变上限积分；牛顿-莱布尼茨公式。'),
(910000000002, @s14, 'KP-014', '定积分的应用', '平面图形面积与旋转体体积；变力做功与路程；微元法的解题步骤。');
