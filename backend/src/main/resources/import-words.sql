-- 词库导入脚本（CET4 核心词汇示例，共100词）
-- 使用方式: mysql -u root -proot timo_words < import-words.sql

INSERT IGNORE INTO words (word, phonetic, pos) VALUES
('abandon', '/əˈbændən/', 'v.'),
('ability', '/əˈbɪləti/', 'n.'),
('absorb', '/əbˈzɔːrb/', 'v.'),
('abstract', '/ˈæbstrækt/', 'adj.'),
('academic', '/ˌækəˈdemɪk/', 'adj.'),
('accelerate', '/əkˈseləreɪt/', 'v.'),
('access', '/ˈækses/', 'n.'),
('accomplish', '/əˈkɑːmplɪʃ/', 'v.'),
('account', '/əˈkaʊnt/', 'n.'),
('accurate', '/ˈækjərət/', 'adj.'),
('achieve', '/əˈtʃiːv/', 'v.'),
('acknowledge', '/əkˈnɑːlɪdʒ/', 'v.'),
('acquire', '/əˈkwaɪər/', 'v.'),
('adapt', '/əˈdæpt/', 'v.'),
('adequate', '/ˈædɪkwət/', 'adj.'),
('adjust', '/əˈdʒʌst/', 'v.'),
('administration', '/ədˌmɪnɪˈstreɪʃn/', 'n.'),
('admire', '/ədˈmaɪər/', 'v.'),
('adopt', '/əˈdɑːpt/', 'v.'),
('advance', '/ədˈvæns/', 'v.'),
('advantage', '/ədˈvæntɪdʒ/', 'n.'),
('aggressive', '/əˈɡresɪv/', 'adj.'),
('agriculture', '/ˈæɡrɪkʌltʃər/', 'n.'),
('alert', '/əˈlɜːrt/', 'adj.'),
('alternative', '/ɔːlˈtɜːrnətɪv/', 'n.'),
('ambiguous', '/æmˈbɪɡjuəs/', 'adj.'),
('ambition', '/æmˈbɪʃn/', 'n.'),
('analyse', '/ˈænəlaɪz/', 'v.'),
('annual', '/ˈænjuəl/', 'adj.'),
('anticipate', '/ænˈtɪsɪpeɪt/', 'v.'),
('anxiety', '/æŋˈzaɪəti/', 'n.'),
('apparent', '/əˈpærənt/', 'adj.'),
('appeal', '/əˈpiːl/', 'v.'),
('appetite', '/ˈæpɪtaɪt/', 'n.'),
('application', '/ˌæplɪˈkeɪʃn/', 'n.'),
('appoint', '/əˈpɔɪnt/', 'v.'),
('appreciate', '/əˈpriːʃieɪt/', 'v.'),
('approach', '/əˈproʊtʃ/', 'v.'),
('appropriate', '/əˈproʊpriət/', 'adj.'),
('approve', '/əˈpruːv/', 'v.'),
('arise', '/əˈraɪz/', 'v.'),
('arrange', '/əˈreɪndʒ/', 'v.'),
('artificial', '/ˌɑːrtɪˈfɪʃl/', 'adj.'),
('assess', '/əˈses/', 'v.'),
('assign', '/əˈsaɪn/', 'v.'),
('assist', '/əˈsɪst/', 'v.'),
('assume', '/əˈsuːm/', 'v.'),
('attach', '/əˈtætʃ/', 'v.'),
('attempt', '/əˈtempt/', 'n.'),
('attitude', '/ˈætɪtuːd/', 'n.'),
('attract', '/əˈtrækt/', 'v.'),
('authority', '/əˈθɔːrəti/', 'n.'),
('automatic', '/ˌɔːtəˈmætɪk/', 'adj.'),
('available', '/əˈveɪləbl/', 'adj.'),
('aware', '/əˈwer/', 'adj.'),
('balance', '/ˈbæləns/', 'n.'),
('ban', '/bæn/', 'v.'),
('barrier', '/ˈbæriər/', 'n.'),
('behalf', '/bɪˈhæf/', 'n.'),
('behave', '/bɪˈheɪv/', 'v.'),
('belief', '/bɪˈliːf/', 'n.'),
('beneath', '/bɪˈniːθ/', 'prep.'),
('benefit', '/ˈbenɪfɪt/', 'n.'),
('blame', '/bleɪm/', 'v.'),
('blend', '/blend/', 'v.'),
('block', '/blɑːk/', 'n.'),
('boom', '/buːm/', 'n.'),
('boost', '/buːst/', 'v.'),
('bound', '/baʊnd/', 'adj.'),
('brake', '/breɪk/', 'n.'),
('breed', '/briːd/', 'v.'),
('brilliant', '/ˈbrɪliənt/', 'adj.'),
('budget', '/ˈbʌdʒɪt/', 'n.'),
('bulk', '/bʌlk/', 'n.'),
('burden', '/ˈbɜːrdn/', 'n.'),
('campaign', '/kæmˈpeɪn/', 'n.'),
('candidate', '/ˈkændɪdeɪt/', 'n.'),
('capable', '/ˈkeɪpəbl/', 'adj.'),
('capacity', '/kəˈpæsəti/', 'n.'),
('capture', '/ˈkæptʃər/', 'v.'),
('cast', '/kæst/', 'v.'),
('category', '/ˈkætəɡɔːri/', 'n.'),
('cease', '/siːs/', 'v.'),
('challenge', '/ˈtʃælɪndʒ/', 'n.'),
('champion', '/ˈtʃæmpiən/', 'n.'),
('channel', '/ˈtʃænl/', 'n.'),
('chapter', '/ˈtʃæptər/', 'n.'),
('character', '/ˈkærəktər/', 'n.'),
('charge', '/tʃɑːrdʒ/', 'v.'),
('charity', '/ˈtʃærəti/', 'n.'),
('chemical', '/ˈkemɪkl/', 'adj.'),
('circulate', '/ˈsɜːrkjəleɪt/', 'v.'),
('civil', '/ˈsɪvl/', 'adj.'),
('clarify', '/ˈklærəfaɪ/', 'v.'),
('climate', '/ˈklaɪmət/', 'n.'),
('colleague', '/ˈkɑːliːɡ/', 'n.'),
('colony', '/ˈkɑːləni/', 'n.'),
('combine', '/kəmˈbaɪn/', 'v.'),
('comfort', '/ˈkʌmfərt/', 'n.'),
('command', '/kəˈmænd/', 'n.'),
('comment', '/ˈkɑːment/', 'n.'),
('commit', '/kəˈmɪt/', 'v.'),
('communicate', '/kəˈmjuːnɪkeɪt/', 'v.'),
('community', '/kəˈmjuːnəti/', 'n.'),
('compare', '/kəmˈper/', 'v.'),
('compete', '/kəmˈpiːt/', 'v.'),
('complex', '/kɑːmˈpleks/', 'adj.'),
('component', '/kəmˈpoʊnənt/', 'n.'),
('compose', '/kəmˈpoʊz/', 'v.'),
('comprehend', '/ˌkɑːmprɪˈhend/', 'v.'),
('concentrate', '/ˈkɑːnsntreɪt/', 'v.'),
('concept', '/ˈkɑːnsept/', 'n.'),
('concern', '/kənˈsɜːrn/', 'n.'),
('conduct', '/kənˈdʌkt/', 'v.'),
('conference', '/ˈkɑːnfərəns/', 'n.');

-- 为每个单词添加一个默认中文释义
INSERT INTO meanings (word_id, meaning, part_of_speech, sort_order)
SELECT w.id, m.meaning, m.pos, 0
FROM words w
JOIN (
    SELECT 'abandon' AS word, '放弃；抛弃' AS meaning, 'v.' AS pos UNION ALL
    SELECT 'ability', '能力；才能', 'n.' UNION ALL
    SELECT 'absorb', '吸收；吸引', 'v.' UNION ALL
    SELECT 'abstract', '抽象的；摘要', 'adj.' UNION ALL
    SELECT 'academic', '学术的；学者', 'adj.' UNION ALL
    SELECT 'accelerate', '加速；促进', 'v.' UNION ALL
    SELECT 'access', '通道；访问', 'n.' UNION ALL
    SELECT 'accomplish', '完成；实现', 'v.' UNION ALL
    SELECT 'account', '账户；描述', 'n.' UNION ALL
    SELECT 'accurate', '准确的；精确的', 'adj.' UNION ALL
    SELECT 'achieve', '达到；实现', 'v.' UNION ALL
    SELECT 'acknowledge', '承认；致谢', 'v.' UNION ALL
    SELECT 'acquire', '获得；学到', 'v.' UNION ALL
    SELECT 'adapt', '适应；改编', 'v.' UNION ALL
    SELECT 'adequate', '充足的；适当的', 'adj.' UNION ALL
    SELECT 'adjust', '调整；适应', 'v.' UNION ALL
    SELECT 'administration', '管理；行政', 'n.' UNION ALL
    SELECT 'admire', '钦佩；欣赏', 'v.' UNION ALL
    SELECT 'adopt', '采纳；收养', 'v.' UNION ALL
    SELECT 'advance', '前进；进步', 'v.' UNION ALL
    SELECT 'advantage', '优势；有利条件', 'n.' UNION ALL
    SELECT 'aggressive', '侵略的；有进取心的', 'adj.' UNION ALL
    SELECT 'agriculture', '农业', 'n.' UNION ALL
    SELECT 'alert', '警觉的；警告', 'adj.' UNION ALL
    SELECT 'alternative', '替代品；选择', 'n.' UNION ALL
    SELECT 'ambiguous', '模糊的；有歧义的', 'adj.' UNION ALL
    SELECT 'ambition', '野心；抱负', 'n.' UNION ALL
    SELECT 'analyse', '分析', 'v.' UNION ALL
    SELECT 'annual', '每年的；年度的', 'adj.' UNION ALL
    SELECT 'anticipate', '预期；期望', 'v.' UNION ALL
    SELECT 'anxiety', '焦虑；不安', 'n.' UNION ALL
    SELECT 'apparent', '明显的；表面上的', 'adj.' UNION ALL
    SELECT 'appeal', '呼吁；吸引', 'v.' UNION ALL
    SELECT 'appetite', '食欲；胃口', 'n.' UNION ALL
    SELECT 'application', '申请；应用', 'n.' UNION ALL
    SELECT 'appoint', '任命；指定', 'v.' UNION ALL
    SELECT 'appreciate', '感激；欣赏', 'v.' UNION ALL
    SELECT 'approach', '接近；方法', 'v.' UNION ALL
    SELECT 'appropriate', '适当的；合适的', 'adj.' UNION ALL
    SELECT 'approve', '批准；赞成', 'v.' UNION ALL
    SELECT 'arise', '出现；产生', 'v.' UNION ALL
    SELECT 'arrange', '安排；整理', 'v.' UNION ALL
    SELECT 'artificial', '人工的；人造的', 'adj.' UNION ALL
    SELECT 'assess', '评估；评定', 'v.' UNION ALL
    SELECT 'assign', '分配；指派', 'v.' UNION ALL
    SELECT 'assist', '协助；帮助', 'v.' UNION ALL
    SELECT 'assume', '假设；承担', 'v.' UNION ALL
    SELECT 'attach', '附上；重视', 'v.' UNION ALL
    SELECT 'attempt', '尝试；企图', 'n.' UNION ALL
    SELECT 'attitude', '态度；看法', 'n.' UNION ALL
    SELECT 'attract', '吸引', 'v.' UNION ALL
    SELECT 'authority', '权威；当局', 'n.' UNION ALL
    SELECT 'automatic', '自动的；无意识的', 'adj.' UNION ALL
    SELECT 'available', '可用的；有空的', 'adj.' UNION ALL
    SELECT 'aware', '意识到的', 'adj.' UNION ALL
    SELECT 'balance', '平衡；余额', 'n.' UNION ALL
    SELECT 'ban', '禁止', 'v.' UNION ALL
    SELECT 'barrier', '障碍；屏障', 'n.' UNION ALL
    SELECT 'behalf', '代表；利益', 'n.' UNION ALL
    SELECT 'behave', '表现；行为', 'v.' UNION ALL
    SELECT 'belief', '信念；信仰', 'n.' UNION ALL
    SELECT 'beneath', '在...下面', 'prep.' UNION ALL
    SELECT 'benefit', '利益；好处', 'n.' UNION ALL
    SELECT 'blame', '责备；归咎', 'v.' UNION ALL
    SELECT 'blend', '混合；融合', 'v.' UNION ALL
    SELECT 'block', '街区；阻塞', 'n.' UNION ALL
    SELECT 'boom', '繁荣；激增', 'n.' UNION ALL
    SELECT 'boost', '促进；增加', 'v.' UNION ALL
    SELECT 'bound', '一定的；受约束的', 'adj.' UNION ALL
    SELECT 'brake', '刹车', 'n.' UNION ALL
    SELECT 'breed', '繁殖；培育', 'v.' UNION ALL
    SELECT 'brilliant', '辉煌的；杰出的', 'adj.' UNION ALL
    SELECT 'budget', '预算', 'n.' UNION ALL
    SELECT 'bulk', '大量；散装', 'n.' UNION ALL
    SELECT 'burden', '负担；重担', 'n.' UNION ALL
    SELECT 'campaign', '运动；战役', 'n.' UNION ALL
    SELECT 'candidate', '候选人', 'n.' UNION ALL
    SELECT 'capable', '有能力的', 'adj.' UNION ALL
    SELECT 'capacity', '容量；能力', 'n.' UNION ALL
    SELECT 'capture', '捕获；夺取', 'v.' UNION ALL
    SELECT 'cast', '投射；演员阵容', 'v.' UNION ALL
    SELECT 'category', '类别；种类', 'n.' UNION ALL
    SELECT 'cease', '停止；终止', 'v.' UNION ALL
    SELECT 'challenge', '挑战', 'n.' UNION ALL
    SELECT 'champion', '冠军；拥护者', 'n.' UNION ALL
    SELECT 'channel', '频道；渠道', 'n.' UNION ALL
    SELECT 'chapter', '章；篇章', 'n.' UNION ALL
    SELECT 'character', '角色；性格；字符', 'n.' UNION ALL
    SELECT 'charge', '收费；指控', 'v.' UNION ALL
    SELECT 'charity', '慈善；慈善机构', 'n.' UNION ALL
    SELECT 'chemical', '化学的', 'adj.' UNION ALL
    SELECT 'circulate', '循环；流通', 'v.' UNION ALL
    SELECT 'civil', '公民的；民事的', 'adj.' UNION ALL
    SELECT 'clarify', '澄清；阐明', 'v.' UNION ALL
    SELECT 'climate', '气候', 'n.' UNION ALL
    SELECT 'colleague', '同事', 'n.' UNION ALL
    SELECT 'colony', '殖民地；群体', 'n.' UNION ALL
    SELECT 'combine', '结合；联合', 'v.' UNION ALL
    SELECT 'comfort', '舒适；安慰', 'n.' UNION ALL
    SELECT 'command', '命令；指挥', 'n.' UNION ALL
    SELECT 'comment', '评论', 'n.' UNION ALL
    SELECT 'commit', '承诺；犯（罪）', 'v.' UNION ALL
    SELECT 'communicate', '交流；传达', 'v.' UNION ALL
    SELECT 'community', '社区；群体', 'n.' UNION ALL
    SELECT 'compare', '比较；对比', 'v.' UNION ALL
    SELECT 'compete', '竞争；比赛', 'v.' UNION ALL
    SELECT 'complex', '复杂的；综合体', 'adj.' UNION ALL
    SELECT 'component', '组成部分；组件', 'n.' UNION ALL
    SELECT 'compose', '组成；作曲', 'v.' UNION ALL
    SELECT 'comprehend', '理解；领会', 'v.' UNION ALL
    SELECT 'concentrate', '集中；专注', 'v.' UNION ALL
    SELECT 'concept', '概念；观念', 'n.' UNION ALL
    SELECT 'concern', '关心；担忧', 'n.' UNION ALL
    SELECT 'conduct', '进行；行为', 'v.' UNION ALL
    SELECT 'conference', '会议', 'n.'
) m ON w.word = m.word;
